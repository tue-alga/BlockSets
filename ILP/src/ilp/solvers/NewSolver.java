package ilp.solvers;

import com.gurobi.gurobi.*;
import ilp.PolygonType;
import model.ArbitraryPolygonSolution;
import model.Solution;
import model.StatementEntityInstance;

import java.util.*;
import java.awt.Point;

public class NewSolver implements Solver {
    GRBEnv env;
    GRBModel model;

    public ArrayList<Integer> entityIds;
    public List<Integer> statementIds;
    public Map<Integer,Integer> entityIdToIdx;
    public Map<Integer,Integer> statementIdToIdx;

    PolygonType polygonType;

    // Constructor that allows you to define your own list of constraints and
    // objective function
    public NewSolver(PolygonType polygonType) {
        this.polygonType = polygonType;
        if (polygonType != PolygonType.Gamma && polygonType != PolygonType.Nabla && polygonType != PolygonType.Orthoconvex) {
            throw new RuntimeException("NewSolver cannot handle polygon type " + polygonType.name());
        }
        try {
            this.env = new GRBEnv();
            this.model = new GRBModel(env);
        } catch (GRBException e) {
            throw new RuntimeException(e);
        }
    }

    enum Direction {
        UP, DOWN, LEFT, RIGHT
    }

    @Override
    public Solution solve(StatementEntityInstance inst, double timeLimit, int dimensions) throws Exception, GRBException {
        int width = dimensions;
        int height = dimensions;

        this.entityIds = new ArrayList<>(inst.entities.keySet());
        this.statementIds = new ArrayList<>(inst.statements.keySet());

        HashMap<Integer, ArrayList<Integer>> statementIdToEntityIds = new HashMap<>();
        for (var eId : entityIds) {
            var statements = inst.entityIndToStatements.get(eId);
            for (var statement : statements) {
                statementIdToEntityIds.computeIfAbsent(statement, k -> new ArrayList<>()).add(eId);
            }
        }
        for (var entityIds : statementIdToEntityIds.values()) {
            Collections.sort(entityIds);
        }
        HashMap<ArrayList<Integer>, ArrayList<Integer>> zones = new HashMap<>();
        for (var sId : statementIdToEntityIds.keySet()) {
            var zone = statementIdToEntityIds.get(sId);
            zones.computeIfAbsent(zone, k -> new ArrayList<>()).add(sId);
        }

        // Group indistinguishable elements
        // This comes down to using the zones of the set system.
        // However, we want to keep one element in each set separate to serve as the root of the flow for that set.
        // We prefer to use elements for this that are alone in their zone anyway.

        // Maps a set of statements to the sets they belong to.
        HashMap<ArrayList<Integer>, ArrayList<Integer>> groupedElements = new HashMap<>();

        class CandidateCenter {
            public int statement;
            public ArrayList<Integer> zone;
            CandidateCenter(int statement, ArrayList<Integer> zone) {
                this.statement = statement;
                this.zone = zone;
            }
        }

        ArrayList<CandidateCenter> candidateCenters = new ArrayList<>();
        for (var zone : zones.keySet()) {
            var statements = zones.get(zone);
            if (statements.size() == 1) {
                candidateCenters.add(new CandidateCenter(statements.getFirst(), zone));
            }
        }

        candidateCenters.sort(Comparator.comparingInt(c -> c.zone.size()));

        // Map entity ID to singleton group with set center
        HashMap<Integer, ArrayList<Integer>> setCenter = new HashMap<>();

        for (var c : candidateCenters) {
            var group = new ArrayList<>(List.of(c.statement));
            boolean used = false;
            for (var eId : c.zone) {
                if (!setCenter.containsKey(eId)) {
                    setCenter.put(eId, group);
                    used = true;
                }
            }

            if (used) {
                groupedElements.put(group, c.zone);
                zones.remove(c.zone);
            }
        }

        // We pick the leftover zone with the most sets without assigned center.
        // We then pick an element of that zone to serve as the root for all those sets.

        while(setCenter.keySet().size() < inst.entities.size()) {
            var zone = zones.keySet().stream().max (Comparator.comparingLong(z -> z.stream().filter(eId -> !setCenter.containsKey(eId)).count()));
            var statement = zones.get(zone.get()).removeFirst();
            var group = new ArrayList<>(List.of(statement));
            groupedElements.put(group, zone.get());
            for (var eId : zone.get()) {
                if (!setCenter.containsKey(eId)) {
                    setCenter.put(eId, group);
                }
            }
        }

        for (var zone : zones.keySet()) {
            var statements = zones.get(zone);
            groupedElements.put(statements, zone);
        }

        var entityToGroups = new HashMap<Integer, ArrayList<ArrayList<Integer>>>();
        for (var group : groupedElements.keySet()) {
            var entities = groupedElements.get(group);
            for (var entity : entities) {
                entityToGroups.computeIfAbsent(entity, k -> new ArrayList<>()).add(group);
            }
        }

        this.entityIdToIdx = new HashMap<>();
        for (int i = 0; i < entityIds.size(); i++) entityIdToIdx.put(entityIds.get(i), i);

        this.statementIdToIdx = new HashMap<>();
        for (int i = 0; i < statementIds.size(); i++) statementIdToIdx.put(statementIds.get(i), i);

        // =======================================
        // ============== VARIABLES ==============
        // =======================================
        // Whether an element is assigned to a grid cell
        HashMap<ArrayList<Integer>, GRBVar[][]> x = new HashMap<>();
        // Whether a set covers a grid cell
        GRBVar[][][] z = new GRBVar[inst.numberOfEntities][width][height];
        // Active row
        GRBVar[][] r_a = new GRBVar[inst.numberOfEntities][height];
        // Row start
        GRBVar[][] r_start = new GRBVar[inst.numberOfEntities][height];
        // Row end
        GRBVar[][] r_end = new GRBVar[inst.numberOfEntities][height];
        // Active column
        GRBVar[][] c_a = new GRBVar[inst.numberOfEntities][width];
        // Column start
        GRBVar[][] c_start = new GRBVar[inst.numberOfEntities][width];
        // Column end
        GRBVar[][] c_end = new GRBVar[inst.numberOfEntities][width];
        // Flow variables
        GRBVar[][][][] f = new GRBVar[inst.numberOfEntities][width][height][4];
        // Total cell coverage by a set
        GRBVar[] C = new GRBVar[inst.numberOfEntities];
        // Rightmost column (equal to width - 1, because we first leftmost column to be used)
        GRBVar c_max = model.addVar(0, width - 1, 0, GRB.INTEGER, "rightmost_column");
        // Topmost row (equal to height - 1)
        GRBVar r_max = model.addVar(0, height - 1, 0, GRB.INTEGER, "topmost_row");

        GRBVar[][][] rActivePosBegin = new GRBVar[inst.numberOfEntities][height][width];
        GRBVar[][][] rActivePosEnd = new GRBVar[inst.numberOfEntities][height][width];
        GRBVar[][][] cActivePosBegin = new GRBVar[inst.numberOfEntities][width][height];
        GRBVar[][][] cActivePosEnd = new GRBVar[inst.numberOfEntities][width][height];

        // x
        for (var group : groupedElements.keySet()) {
            var xG = new GRBVar[width][height];
            x.put(group, xG);
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    xG[i][j] = model.addVar(0, 1, 0, GRB.BINARY, "x_" + group.toString() + "_" + i + "_" + j);
                }
            }
        }

        // a, b, z, f and C
        for (int eIx = 0; eIx < inst.numberOfEntities; ++eIx) {
            C[eIx] = model.addVar(0, width * height, 0, GRB.INTEGER, "C_" + eIx);

            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    z[eIx][i][j] = model.addVar(0, 1, 0, GRB.BINARY, "z_" + eIx + "_" + i + "_" + j);
                    if (j != 0) {
                        f[eIx][i][j][Direction.DOWN.ordinal()] = model.addVar(0, width * height - 1, 0, GRB.INTEGER, "f_" + eIx + "_" + i + "_" + j + "_" + "down");
                    }
                    if (i != 0) {
                        f[eIx][i][j][Direction.LEFT.ordinal()] = model.addVar(0, width * height - 1, 0, GRB.INTEGER, "f_" + eIx + "_" + i + "_" + j + "_" + "left");
                    }
                    if (i != width - 1) {
                        f[eIx][i][j][Direction.RIGHT.ordinal()] = model.addVar(0, width * height - 1, 0, GRB.INTEGER, "f_" + eIx + "_" + i + "_" + j + "_" + "right");
                    }
                    if (j != height - 1) {
                        f[eIx][i][j][Direction.UP.ordinal()] = model.addVar(0, width * height - 1, 0, GRB.INTEGER, "f_" + eIx + "_" + i + "_" + j + "_" + "up");
                    }
                }
                c_a[eIx][i] = model.addVar(0, 1, 0, GRB.BINARY, "c_a_" + eIx + "_" + i);
                c_start[eIx][i] = model.addVar(0, height-1, 0, GRB.INTEGER, "c_start_" + eIx + "_" + i);
                c_end[eIx][i] = model.addVar(0, height-1, 0, GRB.INTEGER, "c_end_" + eIx + "_" + i);
            }
            for (int j = 0; j < height; j++) {
                r_a[eIx][j] = model.addVar(0, 1, 0, GRB.BINARY, "r_a_" + eIx + "_" + j);
                r_start[eIx][j] = model.addVar(0, width-1, 0, GRB.INTEGER, "r_start_" + eIx + "_" + j);
                r_end[eIx][j] = model.addVar(0, width-1, 0, GRB.INTEGER, "r_end_" + eIx + "_" + j);
            }
        }

        // =======================================
        // ============= CONSTRAINTS =============
        // =======================================
        // Statement <-> cell
        for (var group : groupedElements.keySet()) {
            var xG = x.get(group);
            GRBLinExpr expr = new GRBLinExpr();
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    expr.addTerm(1.0, xG[i][j]);
                }
            }
            model.addConstr(expr, '=', group.size(), "unique_element_position");
        }

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                GRBLinExpr expr = new GRBLinExpr();
                for (var group : groupedElements.keySet()) {
                    expr.addTerm(1.0, x.get(group)[i][j]);
                }
                model.addConstr(expr, '<', 1, "injection");
            }
        }

        // z
        for (int eIx = 0; eIx < inst.numberOfEntities; ++eIx) {
            var eId = entityIds.get(eIx);
            var groups = entityToGroups.get(eId);

            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    GRBLinExpr expr = new GRBLinExpr();
                    for (var group : groups) {
                        expr.addTerm(1.0, x.get(group)[i][j]);
                    }
                    model.addConstr(expr, '<', z[eIx][i][j], "set_coverage");
                }
            }
        }

        // Element exclusion
        for (int eIx = 0; eIx < inst.numberOfEntities; ++eIx) {
            int eId = this.entityIds.get(eIx);

            for (var group : groupedElements.keySet()) {
                if (!groupedElements.get(group).contains(eId)) {
                    // Element group is not part of this set, so force it outside
                    // x[sIx][i][j] => !z[eIx][i][j]
                    // z <= (1 - x)
                    for (int i = 0; i < width; i++) {
                        for (int j = 0; j < height; j++) {
                            GRBLinExpr rhs = new GRBLinExpr();
                            rhs.addConstant(1);
                            rhs.addTerm(-1, x.get(group)[i][j]);
                            model.addConstr(z[eIx][i][j], '<', rhs, "element_exclusion");
                        }
                    }
                }
            }
        }

        // Independent sets are represented by disjoint shapes
        for (int eIx1 = 0; eIx1 < inst.numberOfEntities; eIx1++) {
            for (int eIx2 = eIx1 + 1; eIx2 < inst.numberOfEntities; eIx2++) {
                int[] statementsOfEntity1 = inst.entityIndToStatements.get(entityIds.get(eIx1));
                int[] statementsOfEntity2 = inst.entityIndToStatements.get(entityIds.get(eIx2));
                boolean overlap = false;
                for (var s1 : statementsOfEntity1) {
                    for (var s2 : statementsOfEntity2) {
                        if (s1 == s2) {
                            overlap = true;
                            break;
                        }
                    }
                }

                if (!overlap) {
                    for (int i = 0; i < width; ++i) {
                        for (int j = 0; j < height; ++j) {
                            GRBLinExpr expr = new GRBLinExpr();
                            expr.addTerm(1, z[eIx1][i][j]);
                            expr.addTerm(1, z[eIx2][i][j]);
                            model.addConstr(expr, '<', 1, "disjoint_independent_sets");
                        }
                    }
                }
            }
        }

        // Nabla
        if (polygonType == PolygonType.Nabla || polygonType == PolygonType.Gamma) {
            for (int eIx = 0; eIx < inst.numberOfEntities; ++eIx) {
                for (int i = 0; i < width - 1; i++) {
                    var consecutiveActive = model.addVar(0, 1, 0, GRB.BINARY, "consecutive_column_active");
                    model.addGenConstrAnd(consecutiveActive, new GRBVar[]{c_a[eIx][i], c_a[eIx][i + 1]}, "consecutive_active");
                    GRBLinExpr expr = new GRBLinExpr();
                    expr.addTerm(1, c_start[eIx][i]);
                    expr.addTerm(-1, c_start[eIx][i + 1]);
                    model.addGenConstrIndicator(consecutiveActive, 1, expr, '=', 0, "name");
                }
            }
        }

        // Gamma
        if (polygonType == PolygonType.Gamma) {
            for (int eIx = 0; eIx < inst.numberOfEntities; ++eIx) {
                for (int j = 0; j < height - 1; j++) {
                    var consecutiveActive = model.addVar(0, 1, 0, GRB.BINARY, "consecutive_row_active");
                    model.addGenConstrAnd(consecutiveActive, new GRBVar[]{r_a[eIx][j], r_a[eIx][j + 1]}, "consecutive_active");
                    GRBLinExpr expr = new GRBLinExpr();
                    expr.addTerm(1, r_start[eIx][j]);
                    expr.addTerm(-1, r_start[eIx][j + 1]);
                    model.addGenConstrIndicator(consecutiveActive, 1, expr, '=', 0, "name");
                }
            }
        }

        // C
        for (int eIx = 0; eIx < inst.numberOfEntities; ++eIx) {
            GRBLinExpr expr = new GRBLinExpr();
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    expr.addTerm(1, z[eIx][i][j]);
                }
            }
            model.addConstr(C[eIx], '=', expr, "C_definition");
        }

        // flow constraints
        for (int eIx = 0; eIx < inst.numberOfEntities; ++eIx) {
            var eId = entityIds.get(eIx);
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    // Define g which is equal to C if this is a root cell otherwise it is zero.
                    var g = model.addVar(0, width * height, 0, GRB.INTEGER, "g_" + eIx + "_" + i + "_" + j);
                    model.addConstr(g, '<', C[eIx], "g <= C");
                    var rootGroup = setCenter.get(eId);//this.statementIdToIdx.get(inst.entityIndToStatements.get(this.entityIds.get(eIx))[0]);
                    var isRoot = x.get(rootGroup)[i][j];
                    var expr1 = new GRBLinExpr();
                    expr1.addTerm(width * height, isRoot);
                    model.addConstr(g, '<', expr1, "g <= w * h * isRoot?");
                    var expr2 = new GRBLinExpr();
                    expr2.addTerm(1, C[eIx]);
                    expr2.addTerm((width * height), isRoot);
                    expr2.addConstant(-(width * height));
                    model.addConstr(g, '>', expr2, "g >= C - (w*h)(1 - isRoot?)");

                    GRBLinExpr lhs = new GRBLinExpr();

                    // Outgoing flow
                    for (int dir = 0; dir < 4; ++dir) {
                        if (f[eIx][i][j][dir] != null)
                            lhs.addTerm(1, f[eIx][i][j][dir]);
                    }

                    // Incoming flow
                    if (i > 0) {
                        lhs.addTerm(-1, f[eIx][i - 1][j][Direction.RIGHT.ordinal()]);
                    }
                    if (j > 0) {
                        lhs.addTerm(-1, f[eIx][i][j - 1][Direction.UP.ordinal()]);
                    }
                    if (i < width - 1) {
                        lhs.addTerm(-1, f[eIx][i + 1][j][Direction.LEFT.ordinal()]);
                    }
                    if (j < height - 1) {
                        lhs.addTerm(-1, f[eIx][i][j + 1][Direction.DOWN.ordinal()]);
                    }

                    GRBLinExpr rhs = new GRBLinExpr();
                    rhs.addTerm(1, z[eIx][i][j]);
                    rhs.addTerm(-1, g);

                    model.addConstr(lhs, '=', rhs, "net_flow");

                    // Outgoing flow of non-set cells is 0
                    GRBLinExpr lhs2 = new GRBLinExpr();
                    GRBLinExpr rhs2 = new GRBLinExpr();

                    for (int dir = 0; dir < 4; ++dir) {
                        if (f[eIx][i][j][dir] != null)
                            lhs2.addTerm(1, f[eIx][i][j][dir]);
                    }
                    rhs2.addTerm(width * height - 1, z[eIx][i][j]);

                    model.addConstr(lhs2, '<', rhs2, "flow_through_set_cells");
                }
            }
        }

        for (int eIx = 0; eIx < inst.numberOfEntities; ++eIx) {
            // Columns
            for (int i = 0; i < width; i++) {
                GRBLinExpr expr = new GRBLinExpr();

                for (int j = 0; j < height; j++) {
                    expr.addTerm(1, z[eIx][i][j]);
                    model.addConstr(c_a[eIx][i], '>', z[eIx][i][j], "active_column_>=");

                    cActivePosBegin[eIx][i][j] = model.addVar(0, height - 1, 0, GRB.INTEGER, "activePosBegin");
                    cActivePosEnd[eIx][i][j] = model.addVar(0, height - 1, 0, GRB.INTEGER, "activePosEnd");

                    // ape = j * z
                    GRBLinExpr rhsE = new GRBLinExpr();
                    rhsE.addTerm(j, z[eIx][i][j]);
                    model.addConstr(cActivePosEnd[eIx][i][j], '=', rhsE, "activePosEnd");

                    // apb = height - (height - j) * z
                    GRBLinExpr rhsB = new GRBLinExpr();
                    rhsB.addConstant(height - 1);
                    rhsB.addTerm(-(height - 1), z[eIx][i][j]);
                    rhsB.addTerm(j, z[eIx][i][j]);
                    model.addConstr(cActivePosBegin[eIx][i][j], '=', rhsB, "activePosBegin");

                    // orthoconvexity
                    // r_start <= i <= r_end => z = 1
                    GRBVar atLeast = model.addVar(0, 1, 0, GRB.BINARY, "greater");
                    GRBVar atMost = model.addVar(0, 1, 0, GRB.BINARY, "lesser");

                    // atLeast = 1 <=> j >= c_start
                    // r_start <= j + M * (1 - atLeast)
                    // j < c_start => atLeast = 0
                    GRBLinExpr atLeast1Rhs = new GRBLinExpr();
                    atLeast1Rhs.addConstant(j + height);
                    atLeast1Rhs.addTerm(-height, atLeast);
                    model.addConstr(c_start[eIx][i], GRB.LESS_EQUAL, atLeast1Rhs, "atLeast_1");

                    // c_start >= j + 1 - M * atLeast
                    // j >= c_start => atLeast = 1
                    GRBLinExpr atLeast2Rhs = new GRBLinExpr();
                    atLeast2Rhs.addConstant(j + 1);
                    atLeast2Rhs.addTerm(-height, atLeast);
                    model.addConstr(c_start[eIx][i], GRB.GREATER_EQUAL, atLeast2Rhs, "atLeast_2");

                    // atMost = 1 <=> j <= c_end
                    // c_end >= j - M * (1 - atMost)
                    // j > c_end => atMost = 0
                    GRBLinExpr atMost1Rhs = new GRBLinExpr();
                    atMost1Rhs.addConstant(j - height);
                    atMost1Rhs.addTerm(height, atMost);

                    model.addConstr(c_end[eIx][i], GRB.GREATER_EQUAL, atMost1Rhs, "atMost_1");

                    // c_end <= j - 1 + M * atMost
                    // j <= c_end => atMost = 1
                    GRBLinExpr atMost2Rhs = new GRBLinExpr();
                    atMost2Rhs.addConstant(j - 1);
                    atMost2Rhs.addTerm(height, atMost);

                    model.addConstr(c_end[eIx][i], GRB.LESS_EQUAL, atMost2Rhs, "atMost_2");

                    GRBVar inside = model.addVar(0, 1, 0, GRB.BINARY, "inside");
                    model.addGenConstrAnd(inside, new GRBVar[]{atLeast, atMost}, "inside");

                    GRBLinExpr zE = new GRBLinExpr();
                    zE.addTerm(1, z[eIx][i][j]);
                    model.addGenConstrIndicator(inside, 1, zE, '>', 1, "column_convexity");
                }
                model.addGenConstrMin(c_start[eIx][i], cActivePosBegin[eIx][i], height - 1, "c_start");
                model.addGenConstrMax(c_end[eIx][i], cActivePosEnd[eIx][i], 0, "c_end");

                model.addConstr(c_a[eIx][i], '<', expr, "active_column_<=");
                GRBLinExpr RExpr = new GRBLinExpr();
                RExpr.addTerm(1, c_max);
                model.addGenConstrIndicator(c_a[eIx][i], 1, RExpr, '>', i, "rightmost_column_>");
            }

            // Rows
            for (int j = 0; j < height; j++) {
                GRBLinExpr expr = new GRBLinExpr();

                for (int i = 0; i < width; i++) {
                    expr.addTerm(1, z[eIx][i][j]);
                    model.addConstr(r_a[eIx][j], '>', z[eIx][i][j], "active_row_>=");

                    rActivePosBegin[eIx][j][i] = model.addVar(0, width - 1, 0, GRB.INTEGER, "activePosBegin");
                    rActivePosEnd[eIx][j][i] = model.addVar(0, width - 1, 0, GRB.INTEGER, "activePosEnd");

                    // ape = i * z
                    GRBLinExpr rhsE = new GRBLinExpr();
                    rhsE.addTerm(i, z[eIx][i][j]);
                    model.addConstr(rActivePosEnd[eIx][j][i], '=', rhsE, "activePosEnd");

                    // apb = width - (width - i) * z
                    GRBLinExpr rhsB = new GRBLinExpr();
                    rhsB.addConstant(width - 1);
                    rhsB.addTerm(-(width - 1), z[eIx][i][j]);
                    rhsB.addTerm(i, z[eIx][i][j]);
                    model.addConstr(rActivePosBegin[eIx][j][i], '=', rhsB, "activePosBegin");

                    // orthoconvexity
                    // r_start <= i <= r_end => z = 1
                    GRBVar atLeast = model.addVar(0, 1, 0, GRB.BINARY, "greater");
                    GRBVar atMost = model.addVar(0, 1, 0, GRB.BINARY, "lesser");

                    // atLeast = 1 <=> i >= r_start
                    // r_start <= i + M * (1 - atLeast)
                    // i < r_start => atLeast = 0
                    GRBLinExpr atLeast1Rhs = new GRBLinExpr();
                    atLeast1Rhs.addConstant(i + width);
                    atLeast1Rhs.addTerm(-width, atLeast);
                    model.addConstr(r_start[eIx][j], GRB.LESS_EQUAL, atLeast1Rhs, "atLeast_1");

                    // r_start >= i + 1 - M * atLeast
                    // i >= r_start => atLeast = 1
                    GRBLinExpr atLeast2Rhs = new GRBLinExpr();
                    atLeast2Rhs.addConstant(i + 1);
                    atLeast2Rhs.addTerm(-width, atLeast);
                    model.addConstr(r_start[eIx][j], GRB.GREATER_EQUAL, atLeast2Rhs, "atLeast_2");

                    // atMost = 1 <=> i <= r_end
                    // r_end >= i - M * (1 - atMost)
                    // i > r_end => atMost = 0
                    GRBLinExpr atMost1Rhs = new GRBLinExpr();
                    atMost1Rhs.addConstant(i - width);
                    atMost1Rhs.addTerm(width, atMost);

                    model.addConstr(r_end[eIx][j], GRB.GREATER_EQUAL, atMost1Rhs, "atMost_1");

                    // r_end <= i - 1 + M * atMost
                    // i <= r_end => atMost = 1
                    GRBLinExpr atMost2Rhs = new GRBLinExpr();
                    atMost2Rhs.addConstant(i - 1);
                    atMost2Rhs.addTerm(width, atMost);

                    model.addConstr(r_end[eIx][j], GRB.LESS_EQUAL, atMost2Rhs, "atMost_2");

                    GRBVar inside = model.addVar(0, 1, 0, GRB.BINARY, "inside");
                    model.addGenConstrAnd(inside, new GRBVar[]{atLeast, atMost}, "inside");

                    GRBLinExpr zE = new GRBLinExpr();
                    zE.addTerm(1, z[eIx][i][j]);
                    model.addGenConstrIndicator(inside, 1, zE, '>', 1, "row_convexity");
                }

                model.addGenConstrMin(r_start[eIx][j], rActivePosBegin[eIx][j], width - 1, "r_start");
                model.addGenConstrMax(r_end[eIx][j], rActivePosEnd[eIx][j], 0, "r_end");

                model.addConstr(r_a[eIx][j], '<', expr, "active_row_<=");
                GRBLinExpr TExpr = new GRBLinExpr();
                TExpr.addTerm(1, r_max);
                model.addGenConstrIndicator(r_a[eIx][j], 1, TExpr, '>', j, "topmost_row_>");
            }
        }

        GRBLinExpr numActiveTopRows = new GRBLinExpr();
        GRBLinExpr numActiveLeftColumns = new GRBLinExpr();
        for (int eIx = 0; eIx < inst.numberOfEntities; ++eIx) {
            numActiveTopRows.addTerm(1, r_a[eIx][0]);
            numActiveLeftColumns.addTerm(1, c_a[eIx][0]);
        }
        model.addConstr(numActiveTopRows, '>', 1, "active_top_row");
        model.addConstr(numActiveLeftColumns, '>', 1, "active_left_column");


        // =======================================
        // ============== OBJECTIVE ==============
        // =======================================
        GRBLinExpr area = new GRBLinExpr();
        for (int eIx = 0; eIx < inst.numberOfEntities; ++eIx) {
            area.addTerm(1, C[eIx]);
        }

        GRBLinExpr bboxDimensions = new GRBLinExpr();
        bboxDimensions.addTerm(1, r_max);
        bboxDimensions.addTerm(1, c_max);

        GRBLinExpr obj = new GRBLinExpr();

        GRBVar[][] sumComplexity = new GRBVar[inst.numberOfEntities][height - 1];
        for (int eIx = 0; eIx < inst.numberOfEntities; eIx++) {
            for (int j = 0; j < height - 1; j++) {
                // b_active_consecutive: e_j and e_(j+1) are active
                GRBVar b_active_consecutive = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                        "b=1_implies_rows_" + j + "_" + j + 1 + "_are_active");

                // b_active_consecutive => e_j + e_{j+1} = 2
                GRBLinExpr active_expr = new GRBLinExpr();
                active_expr.addTerm(1.0, r_a[eIx][j]);
                active_expr.addTerm(1.0, r_a[eIx][j + 1]);
                model.addGenConstrIndicator(b_active_consecutive, 1, active_expr, GRB.EQUAL,
                        2,
                        "rows_" + j + "_" + j + 1 + "_are_active");

                // e_j + e_{j+1} = 2 => b_active_consecutive
                // e_j + e_{j+1} - b_active_consecutive <= 1 // for some reason this works
                GRBLinExpr otherSide_expr = new GRBLinExpr();
                otherSide_expr.addTerm(1.0, r_a[eIx][j]);
                otherSide_expr.addTerm(1.0, r_a[eIx][j + 1]);
                otherSide_expr.addTerm(-1.0, b_active_consecutive);
                model.addConstr(otherSide_expr, GRB.LESS_EQUAL, 1.0, "quantum logic");

                // different size of consecutive rows (left side)
                GRBVar diff_InLeftSide = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                        "diff_in_left_size");

                GRBVar lesser = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                        "less_in_left_size");
                GRBVar larger = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                        "larger_in_left_size");

                GRBLinExpr difference = new GRBLinExpr();
                difference.addTerm(1.0, r_start[eIx][j]);
                difference.addTerm(-1.0, r_start[eIx][j + 1]);
                model.addGenConstrIndicator(lesser, 1, difference, GRB.LESS_EQUAL, -1.0,
                        "rows_" + j + "_" + j + 1 + "_negative_difference_left_side");
                model.addGenConstrIndicator(larger, 1, difference, GRB.GREATER_EQUAL, 1.0,
                        "rows_" + j + "_" + j + 1 + "_positive_difference_left_side");
                model.addGenConstrIndicator(lesser, 0, difference, GRB.GREATER_EQUAL, 0.0,
                        "rows_" + j + "_" + (j + 1) + "_not_lesser_left");

                model.addGenConstrIndicator(larger, 0, difference, GRB.LESS_EQUAL, 0.0,
                        "rows_" + j + "_" + (j + 1) + "_not_larger_left");

                // diff_inLeftSide => e_{j,0} != e_{j+1,0}
                GRBLinExpr orExpr = new GRBLinExpr();
                orExpr.addTerm(1.0, larger);
                orExpr.addTerm(1.0, lesser);
                model.addGenConstrIndicator(diff_InLeftSide, 1, orExpr, GRB.GREATER_EQUAL,
                        1,
                        "rows_" + j + "_" + j + 1 + "_exists_difference_left_side");

                // diff_inLeftSide <= e_{j,0} != e_{j+1,0}
                // larger or lesser => diff_inLeftSide, eIx.e. diff_inLeftSide >= larger and
                // diff_inLeftSide >= lesser
                GRBLinExpr larger_diff = new GRBLinExpr();
                GRBLinExpr lesser_diff = new GRBLinExpr();

                larger_diff.addTerm(1.0, diff_InLeftSide);
                larger_diff.addTerm(-1.0, larger);

                lesser_diff.addTerm(1.0, diff_InLeftSide);
                lesser_diff.addTerm(-1.0, lesser);

                model.addConstr(larger_diff, GRB.GREATER_EQUAL, 0.0, "ran out of names");
                model.addConstr(lesser_diff, GRB.GREATER_EQUAL, 0.0, "ran out of names2");

                // different size of consecutive rows (right side)
                GRBVar diff_InRightSide = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                        "diff_in_right_size");

                GRBVar lesser_right = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                        "less_in_right_size");
                GRBVar larger_right = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                        "larger_in_right_size");

                GRBLinExpr difference_right = new GRBLinExpr();
                difference_right.addTerm(1.0, r_end[eIx][j]);
                difference_right.addTerm(-1.0, r_end[eIx][j + 1]);
                model.addGenConstrIndicator(lesser_right, 1, difference_right,
                        GRB.LESS_EQUAL, -1.0,
                        "rows_" + j + "_" + j + 1 + "_negative_difference_right_side");
                model.addGenConstrIndicator(larger_right, 1, difference_right,
                        GRB.GREATER_EQUAL,
                        1.0,
                        "rows_" + j + "_" + j + 1 + "_positive_difference_right_side");
                model.addGenConstrIndicator(lesser_right, 0, difference_right, GRB.GREATER_EQUAL, 0.0,
                        "rows_" + j + "_" + (j + 1) + "_not_lesser_right");

                model.addGenConstrIndicator(larger_right, 0, difference_right, GRB.LESS_EQUAL, 0.0,
                        "rows_" + j + "_" + (j + 1) + "_not_larger_right");

                // diff_inRightSide => e_{j,1} != e_{j+1,1}
                GRBLinExpr orExpr_right = new GRBLinExpr();
                orExpr_right.addTerm(1.0, larger_right);
                orExpr_right.addTerm(1.0, lesser_right);
                model.addGenConstrIndicator(diff_InRightSide, 1, orExpr_right,
                        GRB.GREATER_EQUAL, 1,
                        "rows_" + j + "_" + j + 1 + "_exists_difference_right_side");

                // diff_inRightSide <= e_{j,1} != e_{j+1,1}
                // larger_right or lesser_right => diff_inRightSide, eIx.e. diff_inRightSide >=
                // larger_right and diff_inRightSide >= lesser_right
                GRBLinExpr larger_diff_right = new GRBLinExpr();
                GRBLinExpr lesser_diff_right = new GRBLinExpr();

                larger_diff_right.addTerm(1.0, diff_InRightSide);
                larger_diff_right.addTerm(-1.0, larger_right);

                lesser_diff_right.addTerm(1.0, diff_InRightSide);
                lesser_diff_right.addTerm(-1.0, lesser_right);

                model.addConstr(larger_diff_right, GRB.GREATER_EQUAL, 0.0,
                        "ran out of names3");
                model.addConstr(lesser_diff_right, GRB.GREATER_EQUAL, 0.0,
                        "ran out of names4");

                // if b_active_consecutive (j, and j+1 are active), then sumComplexity =
                // diff_InLeftSide + diff_InRightSide
                sumComplexity[eIx][j] = model.addVar(0.0, 2.0, 0.0, GRB.CONTINUOUS,
                        "complexity_consecutive_rows_" + j);
                GRBLinExpr expression = new GRBLinExpr();
                expression.addTerm(1.0, sumComplexity[eIx][j]);
                expression.addTerm(-1.0, diff_InLeftSide);
                expression.addTerm(-1.0, diff_InRightSide);
                model.addGenConstrIndicator(b_active_consecutive, 1, expression, GRB.EQUAL,
                        0,
                        "if_active_consecutive_rows_then_sumComplexity");

                GRBLinExpr sumCompl_expr = new GRBLinExpr();
                sumCompl_expr.addTerm(1.0, sumComplexity[eIx][j]);
                model.addGenConstrIndicator(b_active_consecutive, 0, sumCompl_expr,
                        GRB.EQUAL, 0,
                        "if_active_consecutive_rows_then_sumComplexity");

                obj.addTerm(0.1, sumComplexity[eIx][j]);
            }
        }




        obj.add(area);
        obj.add(bboxDimensions);

        model.setObjective(obj, GRB.MINIMIZE);
        model.set(GRB.DoubleParam.TimeLimit, timeLimit);
        model.optimize();

        int status = model.get(GRB.IntAttr.Status);
        int solCount = model.get(GRB.IntAttr.SolCount);

        // If no feasible solution exists:
        if (status == GRB.Status.INFEASIBLE ||
                status == GRB.Status.INF_OR_UNBD ||
                solCount == 0) {

            return null;
        }

        Point[] sCoords = new Point[inst.numberOfStatements];
        for (var group : groupedElements.keySet()) {
            int currentStatementIx = 0;
            for (int i = 0; i < width; ++i) {
                for (int j = 0; j < height; j++) {
                    if (x.get(group)[i][j].get(GRB.DoubleAttr.X) > 0.5) {
                        int sIx = statementIdToIdx.get(group.get(currentStatementIx++));
                        sCoords[sIx] = new Point(i, j);
                    }
                }
            }
        }
        var entityCells = new ArrayList<ArrayList<Point>>();
        for (int eIx = 0; eIx < inst.numberOfEntities; ++eIx) {
            var thisEntityCells = new ArrayList<Point>();
            for (int i = 0; i < width; ++i) {
                for (int j = 0; j < height; j++) {
                    if (z[eIx][i][j].get(GRB.DoubleAttr.X) > 0.5) {
                        thisEntityCells.add(new Point(i, j));
                    }
                }
            }
            entityCells.add(thisEntityCells);
        }

        var sol = new ArbitraryPolygonSolution(inst, this.entityIds, entityCells, sCoords);

        System.out.println("Number of variables: " + model.get(GRB.IntAttr.NumVars));
        System.out.println("Number of constraints: " + model.get(GRB.IntAttr.NumConstrs));
        System.out.println("Number of general constraints: " + model.get(GRB.IntAttr.NumGenConstrs));
        System.out.println("Bbox dimensions: " + bboxDimensions.getValue());
        for (int eIx = 0; eIx < inst.numberOfEntities; ++eIx) {
            System.out.println("Entity: " + inst.entities.get(eIx));
            System.out.println("area: " + C[eIx].get(GRB.DoubleAttr.X));
//            System.out.println("Begin");
//            for (int j = 0; j < height; ++j) {
//                for (int i = 0; i < width; ++i) {
//                    System.out.print(rActivePosBegin[eIx][j][i].get(GRB.DoubleAttr.X) + " ");
//                }
//                System.out.println();
//            }
//
//            System.out.println("End");
//            for (int j = 0; j < height; ++j) {
//                for (int i = 0; i < width; ++i) {
//                    System.out.print(rActivePosEnd[eIx][j][i].get(GRB.DoubleAttr.X) + " ");
//                }
//                System.out.println();
//            }
            for (int j = 0; j < height; ++j) {
                System.out.println(r_a[eIx][j].get(GRB.DoubleAttr.X) + " Row: " + r_start[eIx][j].get(GRB.DoubleAttr.X) + "  " + r_end[eIx][j].get(GRB.DoubleAttr.X));
                if (j != height - 1) {
                    System.out.println("Complexity: " + sumComplexity[eIx][j].get(GRB.DoubleAttr.X));
                }
            }
        }

        return sol;
    }
}
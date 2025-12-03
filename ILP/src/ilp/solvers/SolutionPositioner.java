package ilp.solvers;

import java.awt.Point;
import java.util.*;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBEnv;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBModel;
import com.gurobi.gurobi.GRBVar;

import ilp.PolygonType;
import model.PolygonSolution;
import model.PositionedSolution;
import model.RectangleSolution;
import model.Solution;

public class SolutionPositioner {
    static final int GRID_WIDTH = 30;
    static final int GRID_HEIGHT = 30;

    public static PositionedSolution computeCompleteSolution(ArrayList<Solution> components, PolygonType polygonType, double timeLimit) {
        final int nSolutions = components.size();
        int[][] solutionCoordinates = new int[nSolutions][2];

        ArrayList<ArrayList<Solution>> componentVariants = new ArrayList<>();

        for (int i = 0; i < nSolutions; i++) {
            ArrayList<Solution> variants = new ArrayList<>();
            var sol = components.get(i);
            addIfDistinct(variants, sol);

            if (polygonType == PolygonType.Arbitrary || polygonType == PolygonType.Orthoconvex) {
                addIfDistinct(variants, sol.mirrored(false, true));
                addIfDistinct(variants, sol.mirrored(true, true));
            }
            if (polygonType == PolygonType.Arbitrary || polygonType == PolygonType.Orthoconvex || polygonType == PolygonType.Nabla) {
                addIfDistinct(variants, sol.mirrored(true, false));
            }
            if (polygonType == PolygonType.Arbitrary || polygonType == PolygonType.Orthoconvex || polygonType == PolygonType.Rectangle) {
                for (int r = 1; r <= (polygonType == PolygonType.Rectangle ? 1 : 3); ++r) {
                    var rSol = sol.copy();
                    for (int rIt = 0; rIt < r; ++rIt) {
                        rSol.rotate90();
                    }
                    addIfDistinct(variants, rSol);
                    if (polygonType != PolygonType.Rectangle) {
                        addIfDistinct(variants, rSol.mirrored(false, true));
                        addIfDistinct(variants, rSol.mirrored(true, false));
                        addIfDistinct(variants, rSol.mirrored(true, true));
                    }
                }
            }
            componentVariants.add(variants);
        }

        try {
            GRBEnv env = new GRBEnv();
            GRBModel model = new GRBModel(env);

            // Variables: component placement
            Map<String, GRBVar> placementVars = new HashMap<>();
            for (int s = 0; s < nSolutions; s++) {
                ArrayList<Solution> variants = componentVariants.get(s);
                for (int v = 0; v < variants.size(); v++) {
                    Solution solVar = variants.get(v);
                    for (int x = 0; x < GRID_WIDTH; x++) {
                        for (int y = 0; y < GRID_HEIGHT; y++) {
                            if (fits(solVar, x, y)) {
                                String varName = "place_" + s + "_" + v + "_" + x + "_" + y;
                                GRBVar var = model.addVar(0, 1, 0, GRB.BINARY, varName);
                                placementVars.put(varName, var);
                            }
                        }
                    }
                }
            }

            // Each component must be placed exactly once (sum over its variants & positions = 1)
            for (int s = 0; s < nSolutions; s++) {
                GRBLinExpr expr = new GRBLinExpr();
                ArrayList<Solution> variants = componentVariants.get(s);
                for (int v = 0; v < variants.size(); v++) {
                    for (int x = 0; x < GRID_WIDTH; x++) {
                        for (int y = 0; y < GRID_HEIGHT; y++) {
                            String key = "place_" + s + "_" + v + "_" + x + "_" + y;
                            if (placementVars.containsKey(key)) {
                                expr.addTerm(1.0, placementVars.get(key));
                            }
                        }
                    }
                }
                model.addConstr(expr, GRB.EQUAL, 1.0, "one_placement_component_" + s);
            }

            // For each grid cell (gx, gy), gather all placements (any variant) that cover it
            Map<String, List<GRBVar>> cellCoveringPlacements = new HashMap<>();

            for (int s = 0; s < nSolutions; s++) {
                ArrayList<Solution> variants = componentVariants.get(s);
                for (int v = 0; v < variants.size(); v++) {
                    Solution solVar = variants.get(v);
                    for (int ox = 0; ox < GRID_WIDTH; ox++) {
                        for (int oy = 0; oy < GRID_HEIGHT; oy++) {
                            String key = "place_" + s + "_" + v + "_" + ox + "_" + oy;
                            if (!placementVars.containsKey(key))
                                continue;

                            GRBVar placeVar = placementVars.get(key);
                            for (Point p : solVar.getCells()) {
                                int gx = ox + p.x;
                                int gy = oy + p.y;
                                if (gx >= 0 && gx < GRID_WIDTH && gy >= 0 && gy < GRID_HEIGHT) {
                                    String cellKey = gx + "," + gy;
                                    cellCoveringPlacements.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(placeVar);
                                }
                            }
                        }
                    }
                }
            }

            // No overlapping i.e. sum of placements covering a cell is at most 1
            for (Map.Entry<String, List<GRBVar>> entry : cellCoveringPlacements.entrySet()) {
                String[] parts = entry.getKey().split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);

                GRBLinExpr expr = new GRBLinExpr();
                for (GRBVar var : entry.getValue()) {
                    expr.addTerm(1.0, var);
                }
                model.addConstr(expr, GRB.LESS_EQUAL, 1.0, "no_overlap_" + x + "_" + y);
            }

            // Bounding box width/height (0-based dimensions)
            GRBVar W = model.addVar(0, GRID_WIDTH, 0, GRB.INTEGER, "W");
            GRBVar H = model.addVar(0, GRID_HEIGHT, 0, GRB.INTEGER, "H");

            // Orthoconvex hull - min and max x per row
            GRBVar[] wMin = new GRBVar[GRID_HEIGHT];
            GRBVar[] wMax = new GRBVar[GRID_HEIGHT];
            GRBVar[] hMin = new GRBVar[GRID_WIDTH];
            GRBVar[] hMax = new GRBVar[GRID_WIDTH];
            int Mw = GRID_WIDTH - 1;
            int Mh = GRID_HEIGHT - 1;

            for (int gy = 0; gy < GRID_HEIGHT; gy++) {
                wMin[gy] = model.addVar(0, GRID_WIDTH - 1, 0, GRB.INTEGER, "wMin_" + gy);
                wMax[gy] = model.addVar(0, GRID_WIDTH - 1, 0, GRB.INTEGER, "wMax_" + gy);
                model.addConstr(wMax[gy], GRB.GREATER_EQUAL, wMin[gy], "rowOrder_" + gy);
            }

            for (int gx = 0; gx < GRID_WIDTH; gx++) {
                hMin[gx] = model.addVar(0, GRID_HEIGHT - 1, 0, GRB.INTEGER, "hMin_" + gx);
                hMax[gx] = model.addVar(0, GRID_HEIGHT - 1, 0, GRB.INTEGER, "hMax_" + gx);
                model.addConstr(hMax[gx], GRB.GREATER_EQUAL, hMin[gx], "columnOrder_" + gx);
            }

            // For each cell that might be occupied, enforce bounding box constraints
            for (Map.Entry<String, List<GRBVar>> entry : cellCoveringPlacements.entrySet()) {
                String[] parts = entry.getKey().split(",");
                int gx = Integer.parseInt(parts[0]);
                int gy = Integer.parseInt(parts[1]);

                for (GRBVar var : entry.getValue()) {
                    GRBLinExpr exprW = new GRBLinExpr();
                    exprW.addTerm(gx, var);
                    model.addConstr(W, GRB.GREATER_EQUAL, exprW, "boundW_" + gx + "_" + gy);

                    GRBLinExpr exprH = new GRBLinExpr();
                    exprH.addTerm(gy, var);
                    model.addConstr(H, GRB.GREATER_EQUAL, exprH, "boundH_" + gx + "_" + gy);

                    GRBLinExpr wMaxExpr = new GRBLinExpr();
                    wMaxExpr.addTerm(gx, var);
                    model.addConstr(wMax[gy], GRB.GREATER_EQUAL, wMaxExpr, "rowMax_" + gx + "_" + gy);

                    GRBLinExpr hMaxExpr = new GRBLinExpr();
                    hMaxExpr.addTerm(gy, var);
                    model.addConstr(hMax[gx], GRB.GREATER_EQUAL, hMaxExpr, "columnMax_" + gx + "_" + gy);

                    // wMin[gy] ≤ gx + M * (1 - p)
                    GRBLinExpr wMinExpr = new GRBLinExpr();
                    wMinExpr.addTerm(1.0, wMin[gy]);
                    wMinExpr.addTerm(Mw, var);
                    wMinExpr.addConstant(-Mw);
                    model.addConstr(wMinExpr, GRB.LESS_EQUAL, gx, "rowMin_" + gx + "_" + gy);

                    GRBLinExpr hMinExpr = new GRBLinExpr();
                    hMinExpr.addTerm(1.0, hMin[gx]);
                    hMinExpr.addTerm(Mh, var);
                    hMinExpr.addConstant(-Mh);
                    model.addConstr(hMinExpr, GRB.LESS_EQUAL, gy, "columnMin_" + gx + "_" + gy);
                }
            }

            // Objective: Minimize W + H + orthoconvex hull area + aspect diff
            GRBLinExpr totalSize = new GRBLinExpr();
            totalSize.addTerm(GRID_HEIGHT, W);
            totalSize.addTerm(GRID_WIDTH, H);

            GRBLinExpr orthoconvexHullArea = new GRBLinExpr();
            for (int gy = 0; gy < GRID_HEIGHT; gy++) {
                orthoconvexHullArea.addTerm(1.0, wMax[gy]);
                orthoconvexHullArea.addTerm(-1.0, wMin[gy]);
            }
            for (int gx = 0; gx < GRID_WIDTH; gx++) {
                orthoconvexHullArea.addTerm(1.0, hMax[gx]);
                orthoconvexHullArea.addTerm(-1.0, hMin[gx]);
            }

            // Squareness
            GRBVar A = model.addVar(0.0, GRID_WIDTH, 0.0, GRB.CONTINUOUS, "AspectDiff");
            GRBLinExpr expr1 = new GRBLinExpr();
            expr1.addTerm(1.0, A);
            expr1.addTerm(1.0, W);
            expr1.addTerm(-1.0, H);

            GRBLinExpr expr2 = new GRBLinExpr();
            expr2.addTerm(1.0, A);
            expr2.addTerm(-1.0, W);
            expr2.addTerm(1.0, H);

            model.addConstr(expr1, GRB.GREATER_EQUAL, 0, "A_ge_HW");
            model.addConstr(expr2, GRB.GREATER_EQUAL, 0, "A_ge_WH");

            GRBLinExpr aspectRatioDiff = new GRBLinExpr();
            aspectRatioDiff.addTerm(1, A);

            GRBLinExpr obj = new GRBLinExpr();
            obj.add(totalSize);
            obj.add(aspectRatioDiff);
            obj.add(orthoconvexHullArea);

            model.set(GRB.DoubleParam.TimeLimit, timeLimit);
            model.setObjective(obj, GRB.MINIMIZE);
            model.optimize();

            int status = model.get(GRB.IntAttr.Status);
            System.out.println("Gurobi status: " + status);
            if (status != GRB.OPTIMAL && status == GRB.INFEASIBLE && status != GRB.SUBOPTIMAL) {
                System.err.println("Model did not return a usable solution (status=" + status + "). Aborting extraction.");
                model.dispose();
                env.dispose();
                return null;
            }

            int[] chosenVariant = new int[nSolutions];
            int[][] chosenPos = new int[nSolutions][2];
            Arrays.fill(chosenVariant, -1);
            for (int s = 0; s < nSolutions; s++) {
                double bestVal = -1.0;
                String bestKey = null;
                // scan all placement vars for component s
                String prefix = "place_" + s + "_";
                for (Map.Entry<String, GRBVar> e : placementVars.entrySet()) {
                    String key = e.getKey();
                    if (!key.startsWith(prefix)) continue;
                    GRBVar var = e.getValue();
                    double val = var.get(GRB.DoubleAttr.X);
                    if (val > bestVal) {
                        bestVal = val;
                        bestKey = key;
                    }
                }
                if (bestKey == null || bestVal <= 0.5) {
                    System.err.println("No integer placement found for component " + s + " (bestVal=" + bestVal + "). Aborting.");
                    // Option: continue and leave it unplaced, but safer to abort.
                    model.dispose();
                    env.dispose();
                    return null;
                }
                // parse the chosen key "place_s_v_x_y"
                String[] parts = bestKey.split("_");
                int v = Integer.parseInt(parts[2]);
                int x = Integer.parseInt(parts[3]);
                int y = Integer.parseInt(parts[4]);

                chosenVariant[s] = v;
                chosenPos[s][0] = x;
                chosenPos[s][1] = y;
            }

            for (int s = 0; s < nSolutions; s++) {
                int v = chosenVariant[s];
                Solution chosen = componentVariants.get(s).get(v);
                components.set(s, chosen.copy());
                solutionCoordinates[s][0] = chosenPos[s][0];
                solutionCoordinates[s][1] = chosenPos[s][1];
            }

            // Apply offsets to set component coordinates in the global placement
            offsetCoords(components, solutionCoordinates);

            System.out.println("Bounding Box: " + W.get(GRB.DoubleAttr.X) + " x " + H.get(GRB.DoubleAttr.X));

            return new PositionedSolution(components, (int) W.get(GRB.DoubleAttr.X), (int) H.get(GRB.DoubleAttr.X));
        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " + e.getMessage());
        }

        return null;
    }

    private static void addIfDistinct(ArrayList<Solution> sols, Solution sol) {
        for (Solution s : sols) {
            if (sol.occupySameCells(s)) {
                return;
            }
        }
        sols.add(sol);
    }

    // Check if a component can be placed in this grid position
    private static boolean fits(Solution sol, int offsetX, int offsetY) {
        for (Point p : sol.getCells()) {
            int x = offsetX + p.x;
            int y = offsetY + p.y;
            if (x < 0 || x >= GRID_WIDTH || y < 0 || y >= GRID_HEIGHT)
                return false;
        }
        return true;
    }

    // Shift coordinates of all component cells based on that component's position
    // in the grid
    private static void offsetCoords(ArrayList<Solution> components, int[][] offsets) {
        for (int s = 0; s < components.size(); s++) {
            int x = offsets[s][0];
            int y = offsets[s][1];

            if (components.get(s) instanceof RectangleSolution rs) {
                for (int[] entity : rs.entityCoordinates) {
                    entity[0] += x;
                    entity[1] += y;
                    entity[2] += x;
                    entity[3] += y;
                }

                for (int[] statement : rs.statementCoordinates) {
                    statement[0] += x;
                    statement[1] += y;
                }
            } else if (components.get(s) instanceof PolygonSolution ps) {
                for (int[][] entity : ps.entities) {
                    // Shift x coordinates
                    for (int i = 0; i < entity.length; i++) {
                        entity[i][1] += x;
                        entity[i][2] += x;
                    }
                   
                    // Replace the active row booleans with integers storing the y coordinate of the
                    // row in the overall solution
                    // Note: y coordinates are artificially increased by 1 to differentiate them
                    // from inactive rows
                    for (int i = 0; i < entity.length; i++) {
                        if (entity[i][0] == 1) {
                            entity[i][0] += i + y;
                        }
                    }
                }

                for (int[] statement : ps.statementCoordinates) {
                    statement[0] += x;
                    statement[1] += y;
                }
            }
        }
    }
}
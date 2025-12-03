package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBVar;

import ilp.ModelContext;
import ilp.variables.VarsRectangles;

// Make entities with non-overlapping statements not overlap coordinates (H6)
public class H6DisjointEntitiesDoNotOverlap implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsRectangles v)) { // only add this constraint for rectangle solutions
            int nEntities = ctx.entityIds.size();
            final int M = ctx.bigM;

            for (int i = 0; i < nEntities; i++) {
                for (int j = i + 1; j < nEntities; j++) {
                    int[] statementsOfEntity1 = ctx.inst.entityIndToStatements.get(ctx.entityIds.get(i));
                    int[] statementsOfEntity2 = ctx.inst.entityIndToStatements.get(ctx.entityIds.get(j));
                    boolean overlap = false;
                    for (int k = 0; k < statementsOfEntity1.length; k++) {
                        for (int l = 0; l < statementsOfEntity2.length; l++) {
                            if (statementsOfEntity1[k] == statementsOfEntity2[l]) {
                                overlap = true;
                                break;
                            }
                        }
                    }

                    if (!overlap) {

                        GRBVar[] vars = new GRBVar[4];
                        for (int k = 0; k < 4; k++) {
                            vars[k] = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "H6_" + i + "_" + j + "_" + k);
                        }

                        // x1_e1 - x2_e2 + M * vars[0] >= 0
                        GRBLinExpr expr = new GRBLinExpr();
                        expr.addTerm(-1.0, v.entityCoordinates[j][2]);
                        expr.addTerm(1.0, v.entityCoordinates[i][0]);
                        expr.addTerm(M, vars[0]);
                        ctx.model.addConstr(expr, GRB.GREATER_EQUAL, 0, "H6_" + i + "_" + j + "_left");

                        // x2_e1 - x1_e2 + M * vars[1] >= 0
                        expr = new GRBLinExpr();
                        expr.addTerm(1.0, v.entityCoordinates[j][0]);
                        expr.addTerm(-1.0, v.entityCoordinates[i][2]);
                        expr.addTerm(M, vars[1]);
                        ctx.model.addConstr(expr, GRB.GREATER_EQUAL, 0, "H6_" + i + "_" + j + "_right");

                        // y1_e1 - y2_e2 + M * vars[2] >= 0
                        expr = new GRBLinExpr();
                        expr.addTerm(-1.0, v.entityCoordinates[j][3]);
                        expr.addTerm(1.0, v.entityCoordinates[i][1]);
                        expr.addTerm(M, vars[2]);
                        ctx.model.addConstr(expr, GRB.GREATER_EQUAL, 0, "H6_" + i + "_" + j + "_top");

                        // y1_e2 - y2_e1 + M * vars[3] >= 0
                        expr = new GRBLinExpr();
                        expr.addTerm(1.0, v.entityCoordinates[j][1]);
                        expr.addTerm(-1.0, v.entityCoordinates[i][3]);
                        expr.addTerm(M, vars[3]);
                        ctx.model.addConstr(expr, GRB.GREATER_EQUAL, 0, "H6_" + i + "_" + j + "_bottom");

                        expr = new GRBLinExpr();
                        expr.addTerm(1.0, vars[0]);
                        expr.addTerm(1.0, vars[1]);
                        expr.addTerm(1.0, vars[2]);
                        expr.addTerm(1.0, vars[3]);
                        ctx.model.addConstr(expr, GRB.LESS_EQUAL, 3.0, "H6_" + i + "_" + j + "_sum");
                    }
                }
            }
        }
    }
}

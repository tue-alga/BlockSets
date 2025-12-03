package ilp.constraints;

import java.util.Arrays;
import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBVar;
import ilp.ModelContext;
import ilp.variables.VarsRectangles;

// Keep statements outside of entities they do not belong to (H2)
public class H2OutsideNonMembers implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsRectangles v)) { // only add this constraint for rectangle solutions
            int nStatements = ctx.inst.numberOfStatements;
            int nEntities = ctx.entityIds.size();
            final int M = ctx.bigM;

            for (int i = 0; i < nEntities; i++) {
                int entityId = ctx.entityIds.get(i);

                for (int j = 0; j < nStatements; j++) {
                    final int statementIdFinal = ctx.statementIds.get(j);
                    if (Arrays.stream(ctx.inst.entityIndToStatements.get(entityId))
                            .noneMatch(x -> x == statementIdFinal)) {

                        GRBVar[] vars = new GRBVar[4];
                        for (int k = 0; k < 4; k++) {
                            vars[k] = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "H2_" + i + "_" + j + "_" + k);
                        }

                        // x - x1 + M * vars[0] >= 0
                        GRBLinExpr expr = new GRBLinExpr();
                        expr.addTerm(-1.0, v.statementCoordinates[j][0]);
                        expr.addTerm(1.0, v.entityCoordinates[i][0]);
                        expr.addTerm(M, vars[0]);
                        ctx.model.addConstr(expr, GRB.GREATER_EQUAL, 1, "H2_" + i + "_" + j + "_left");

                        // x2 - x + M * vars[1] >= 0
                        expr = new GRBLinExpr();
                        expr.addTerm(1.0, v.statementCoordinates[j][0]);
                        expr.addTerm(-1.0, v.entityCoordinates[i][2]);
                        expr.addTerm(M, vars[1]);
                        ctx.model.addConstr(expr, GRB.GREATER_EQUAL, 1, "H2_" + i + "_" + j + "_right");

                        // y1 - y + M * vars[2] >= 0
                        expr = new GRBLinExpr();
                        expr.addTerm(-1.0, v.statementCoordinates[j][1]);
                        expr.addTerm(1.0, v.entityCoordinates[i][1]);
                        expr.addTerm(M, vars[2]);
                        ctx.model.addConstr(expr, GRB.GREATER_EQUAL, 1, "H2_" + i + "_" + j + "_top");

                        // y - y2 + M * vars[3] >= 0
                        expr = new GRBLinExpr();
                        expr.addTerm(1.0, v.statementCoordinates[j][1]);
                        expr.addTerm(-1.0, v.entityCoordinates[i][3]);
                        expr.addTerm(M, vars[3]);
                        ctx.model.addConstr(expr, GRB.GREATER_EQUAL, 1, "H2_" + i + "_" + j + "_bottom");

                        expr = new GRBLinExpr();
                        expr.addTerm(1.0, vars[0]);
                        expr.addTerm(1.0, vars[1]);
                        expr.addTerm(1.0, vars[2]);
                        expr.addTerm(1.0, vars[3]);
                        ctx.model.addConstr(expr, GRB.LESS_EQUAL, 3.0, "H2_" + i + "_" + j + "_sum");
                    }
                }
            }
        }
    }
}

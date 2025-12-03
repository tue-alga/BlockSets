package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import ilp.ModelContext;
import ilp.variables.VarsRectangles;

public class H1StatementsInsideEntities implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsRectangles v)) { // only add this constraint for rectangle solutions
            int nEntities = ctx.entityIds.size();
            // Positioning statements inside entities (H1)
            for (int i = 0; i < nEntities; i++) {
                int entityId = ctx.entityIds.get(i);
                int[] statementsOfEntity = ctx.inst.entityIndToStatements.get(entityId);
                for (int j = 0; j < statementsOfEntity.length; j++) {
                    int statementIndex = ctx.statementIdToIdx.get(statementsOfEntity[j]);

                    // statement x >= entity x1 (right of entity's left side)
                    GRBLinExpr expr = new GRBLinExpr();
                    expr.addTerm(1.0, v.statementCoordinates[statementIndex][0]);
                    expr.addTerm(-1.0, v.entityCoordinates[i][0]);
                    ctx.model.addConstr(expr, GRB.GREATER_EQUAL, 0, "H1_" + i + "_" + statementIndex + "_x1");

                    // statement y >= entity y1 (below entity's top side)
                    expr = new GRBLinExpr();
                    expr.addTerm(1.0, v.statementCoordinates[statementIndex][1]);
                    expr.addTerm(-1.0, v.entityCoordinates[i][1]);
                    ctx.model.addConstr(expr, GRB.GREATER_EQUAL, 0, "H1_" + i + "_" + statementIndex + "_y1");

                    // statement x <= entity x2 (left of entity's right side)
                    expr = new GRBLinExpr();
                    expr.addTerm(1.0, v.entityCoordinates[i][2]);
                    expr.addTerm(-1.0, v.statementCoordinates[statementIndex][0]);
                    ctx.model.addConstr(expr, GRB.GREATER_EQUAL, 0, "H1_" + i + "_" + statementIndex + "_x2");

                    // statement y <= entity y2 (above entity's bottom side)
                    expr = new GRBLinExpr();
                    expr.addTerm(1.0, v.entityCoordinates[i][3]);
                    expr.addTerm(-1.0, v.statementCoordinates[statementIndex][1]);
                    ctx.model.addConstr(expr, GRB.GREATER_EQUAL, 0, "H1_" + i + "_" + statementIndex + "_y2");
                }
            }
        }
    }
}

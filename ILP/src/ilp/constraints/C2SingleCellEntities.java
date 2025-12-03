package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;

import ilp.ModelContext;
import ilp.variables.VarsRectangles;

// Single statement entities take up only 1 cell (C2)
public class C2SingleCellEntities implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsRectangles v)) { // only add this constraint for rectangle solutions
            for (int i = 0; i < ctx.entityIds.size(); i++) {
                if (ctx.inst.entityIndToStatements.get(ctx.entityIds.get(i)).length == 1) {
                    GRBLinExpr expr = new GRBLinExpr();
                    expr.addTerm(1.0, v.entityCoordinates[i][2]);
                    expr.addTerm(-1.0, v.entityCoordinates[i][0]);
                    ctx.model.addConstr(expr, GRB.EQUAL, 0, "C2_" + i + "_w");

                    expr = new GRBLinExpr();
                    expr.addTerm(1.0, v.entityCoordinates[i][3]);
                    expr.addTerm(-1.0, v.entityCoordinates[i][1]);
                    ctx.model.addConstr(expr, GRB.EQUAL, 0, "C2_" + i + "_h");
                }
            }
        }
    }
}

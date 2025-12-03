package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;

import ilp.ModelContext;
import ilp.variables.VarsRectangles;

// Max height constraint used in the objective function (H9)
public class H9MaxHeight implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsRectangles v)) { // only add this constraint for rectangle solutions
            for (int i = 0; i < ctx.entityIds.size(); i++) {
                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(1.0, v.maxHeight);
                expr.addTerm(-1.0, v.entityCoordinates[i][3]);
                ctx.model.addConstr(expr, GRB.GREATER_EQUAL, 0, "H9_" + i + "_h");

            }
        }
    }
}
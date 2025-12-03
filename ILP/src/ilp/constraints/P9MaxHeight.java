package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

public class P9MaxHeight implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only add this constraint for polygon solutions
            // if e_j = 1 => maxHeight >= j
            for (int i = 0; i < ctx.entityIds.size(); i++) {
                for (int j = 0; j <= ctx.dimensions; j++) {
                    GRBLinExpr maxHeight_expr = new GRBLinExpr();
                    maxHeight_expr.addTerm(1.0, v.maxHeight);
                    ctx.model.addGenConstrIndicator(v.entities[i].activeRows[j], 1, maxHeight_expr,
                            GRB.GREATER_EQUAL, j, "P9_" + i + "_h");
                }
            }
        }
    }
}

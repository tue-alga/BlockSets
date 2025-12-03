package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

public class P1ConsecutiveEntityRows implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only add this constraint for polygon solutions
            int nEntities = ctx.entityIds.size();

            // if rows j-1 and j+1 are active, then row j should also be active
            // e_j >= e_(j-1) + e_(j+1) - 1
            for (int i = 0; i < nEntities; i++) {
                for (int s = 0; s < ctx.dimensions - 1; s++) {
                    for (int f = s + 2; f <= ctx.dimensions; f++) {
                        for (int k = s + 1; k < f; k++) {
                            GRBLinExpr rhs = new GRBLinExpr();
                            rhs.addTerm(1.0, v.entities[i].activeRows[s]);
                            rhs.addTerm(1.0, v.entities[i].activeRows[f]);
                            rhs.addConstant(-1.0);

                            ctx.model.addConstr(v.entities[i].activeRows[k], GRB.GREATER_EQUAL, rhs,
                                    "e_" + i + "nonempty_row_" + k);
                        }
                    }
                }
            }
        }
    }

}

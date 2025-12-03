package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBVar;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

public class P12bRowStartMonotonicity implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only add this constraint for polygon solutions
            for (int i = 0; i < ctx.entityIds.size(); i++) {
                GRBVar increasing = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "start_increasing_" + i);
                for (int r = 0; r < ctx.dimensions; r++) {
                    GRBLinExpr diff = new GRBLinExpr();
                    diff.addTerm(1.0, v.entities[i].rowBounds[r][0]);
                    diff.addTerm(-1.0, v.entities[i].rowBounds[r + 1][0]);

                    // If increasing=1 -> rowBounds[r][1] <= rowBounds[r+1][1]
                    ctx.model.addGenConstrIndicator(increasing, 1, diff,
                            GRB.LESS_EQUAL, 0.0, "monoInc_" + i + "_" + r);

                    // If increasing=0 -> enforce rowBounds[r][1] >= rowBounds[r+1][1]
                    ctx.model.addGenConstrIndicator(increasing, 0, diff,
                            GRB.GREATER_EQUAL, 0.0, "monoDec_" + i + "_" + r);
                }
            }
        }
    }
}

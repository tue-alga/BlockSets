package ilp.objective;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;

import ilp.ModelContext;
import ilp.variables.VarsRectangles;

public class CompactSquareTopLeft implements ObjectiveModule {

    @Override
    public void apply(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsRectangles v)) { // only use this objective function for rectangle solutions
            GRBLinExpr MINIMIZE_ME = new GRBLinExpr();
            MINIMIZE_ME.addTerm(1.0, v.diff);
            MINIMIZE_ME.addTerm(2.0, v.maxHeight);
            MINIMIZE_ME.addTerm(2.0, v.maxWidth);

            for (int i = 0; i < ctx.entityIds.size(); i++) {
                MINIMIZE_ME.addTerm(1.0, v.entityCoordinates[i][2]);
                MINIMIZE_ME.addTerm(-1.0, v.entityCoordinates[i][0]);
                MINIMIZE_ME.addTerm(1.0, v.entityCoordinates[i][3]);
                MINIMIZE_ME.addTerm(-1.0, v.entityCoordinates[i][1]);
            }

            // Favor top left
            for (int i = 0; i < ctx.statementIds.size(); i++) {
                MINIMIZE_ME.addTerm(0.5, v.statementCoordinates[i][0]);
                MINIMIZE_ME.addTerm(0.5, v.statementCoordinates[i][1]);
            }

            ctx.model.setObjective(MINIMIZE_ME, GRB.MINIMIZE);
        }
    }
}

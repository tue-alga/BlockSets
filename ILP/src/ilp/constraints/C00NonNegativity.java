package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import ilp.ModelContext;
import ilp.variables.VarsRectangles;

/** All coordinates are non-negative (C00) */
public final class C00NonNegativity implements ConstraintModule {
  @Override
  public void add(ModelContext ctx) throws GRBException {
    if ((ctx.v instanceof VarsRectangles v)) { // only add this constraint for rectangle solutions
      int nS = ctx.statementIds.size(), nE = ctx.entityIds.size();
      for (int i = 0; i < nS; i++) {
        ctx.model.addConstr(v.statementCoordinates[i][0], GRB.GREATER_EQUAL, 0, "C00_" + i + "_x");
        ctx.model.addConstr(v.statementCoordinates[i][1], GRB.GREATER_EQUAL, 0, "C00_" + i + "_y");
      }
      for (int i = 0; i < nE; i++) {
        for (int j = 0; j < 4; j++) {
          ctx.model.addConstr(v.entityCoordinates[i][j], GRB.GREATER_EQUAL, 0, "C00_e_" + i + "_" + j);
        }
      }
    }
  }
}
package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import ilp.ModelContext;
import ilp.variables.VarsRectangles;

/** All coordinates are at most gridMax (C01) */
public final class C01UpperBound implements ConstraintModule {
  @Override
  public void add(ModelContext ctx) throws GRBException {
    if ((ctx.v instanceof VarsRectangles v)) { // only add this constraint for rectangle solutions
      int nS = ctx.statementIds.size(), nE = ctx.entityIds.size();
      int U = ctx.gridMax;
      for (int i = 0; i < nS; i++) {
        ctx.model.addConstr(v.statementCoordinates[i][0], GRB.LESS_EQUAL, U, "C01_" + i + "_x");
        ctx.model.addConstr(v.statementCoordinates[i][1], GRB.LESS_EQUAL, U, "C01_" + i + "_y");
      }
      for (int i = 0; i < nE; i++) {
        for (int j = 0; j < 4; j++) {
          ctx.model.addConstr(v.entityCoordinates[i][j], GRB.LESS_EQUAL, U, "C01_e_" + i + "_" + j);
        }
      }
    }
  }
}

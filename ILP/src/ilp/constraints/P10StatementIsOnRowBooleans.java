package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBVar;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

public class P10StatementIsOnRowBooleans implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only add this constraint for polygon solutions
            int nStatements = ctx.statementIds.size();
            for (int i = 0; i < nStatements; i++) {
                for (int j = 0; j <= ctx.dimensions; j++) {
                    // If isOnRow_j == 1 then s_y == j
                    GRBLinExpr s_y = new GRBLinExpr();
                    s_y.addTerm(1.0, v.statementCoordinates[i][1]);
                    ctx.model.addGenConstrIndicator(v.statementIsOnRow[i][j], 1, s_y, GRB.EQUAL, j,
                            "row_match_" + j);

                    // if isOnRow_j = 0 then s_y != j
                    GRBVar s_y_larger = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "s_y_larger");
                    ctx.model.addGenConstrIndicator(s_y_larger, 1, s_y, GRB.GREATER_EQUAL, j + 1, "blah blah blah");
                    GRBVar s_y_smaller = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "s_y_larger");
                    ctx.model.addGenConstrIndicator(s_y_smaller, 1, s_y, GRB.LESS_EQUAL, j - 1, "blah blah blah2");
                    GRBLinExpr orExpr = new GRBLinExpr();
                    orExpr.addTerm(1.0, s_y_larger);
                    orExpr.addTerm(1.0, s_y_smaller);
                    ctx.model.addGenConstrIndicator(v.statementIsOnRow[i][j], 0, orExpr, GRB.GREATER_EQUAL, 1.0,
                            "is_onRow_j = 0 => s_y != " + j);
                }
            }
        }
    }
}

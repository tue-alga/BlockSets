package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBVar;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

public class P11EqualRowStart implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only add this constraint for polygon solutions
            int nEntities = ctx.entityIds.size();
            for (int i = 0; i < nEntities; i++) {
                for (int row1 = 0; row1 < ctx.dimensions; row1++) {
                    for (int row2 = row1 + 1; row2 <= ctx.dimensions; row2++) {

                        // bothActive = activeRows[row1] AND activeRows[row2]
                        GRBVar bothActive = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                "bothActive_" + i + "_" + row1);

                        // bothActive <= activeRows[j]
                        ctx.model.addConstr(bothActive, GRB.LESS_EQUAL, v.entities[i].activeRows[row1],
                                "and_link1_" + i + "_" + row1);

                        // bothActive <= activeRows[j+1]
                        ctx.model.addConstr(bothActive, GRB.LESS_EQUAL, v.entities[i].activeRows[row2],
                                "and_link2_" + i + "_" + row1);

                        // bothActive >= activeRows[j] + activeRows[j+1] - 1
                        GRBLinExpr rhs = new GRBLinExpr();
                        rhs.addTerm(1.0, v.entities[i].activeRows[row1]);
                        rhs.addTerm(1.0, v.entities[i].activeRows[row2]);
                        rhs.addConstant(-1.0);
                        ctx.model.addConstr(bothActive, GRB.GREATER_EQUAL, rhs,
                                "and_link3_" + i + "_" + row1);

                        // Now enforce equality iff bothActive=1
                        GRBLinExpr diff = new GRBLinExpr();
                        diff.addTerm(1.0, v.entities[i].rowBounds[row1][0]);
                        diff.addTerm(-1.0, v.entities[i].rowBounds[row2][0]);

                        // Indicator: if bothActive=1 then diff == 0
                        ctx.model.addGenConstrIndicator(bothActive, 1, diff, GRB.EQUAL, 0.0,
                                "equalBounds_" + i + "_" + row1);

                    }
                }
            }
        }
    }

}

package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBVar;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

public class P12aRowEndMonotonicity implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only add this constraint for polygon solutions
            for (int i = 0; i < ctx.entityIds.size(); i++) {
                // Move this variable declaration before the for loop if you want all entities
                // to have the same direction
                GRBVar increasing = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "end_increasing_" + i);

                GRBVar notInc = ctx.model.addVar(0, 1, 0, GRB.BINARY, "notInc");

                // notInc = 1 - increasing
                GRBLinExpr notExpr = new GRBLinExpr();
                notExpr.addTerm(1.0, increasing);
                notExpr.addTerm(1.0, notInc);
                ctx.model.addConstr(notExpr, GRB.EQUAL, 1.0, "not_increasing");

                for (int r = 0; r < ctx.dimensions; r++) {
                    GRBLinExpr diff = new GRBLinExpr();
                    diff.addTerm(1.0, v.entities[i].rowBounds[r][1]);
                    diff.addTerm(-1.0, v.entities[i].rowBounds[r + 1][1]);
                    
                    GRBVar bothActive_increasing = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            "active_rows_" + r + "_" + (r + 1) + "_ent_" + i);

                    GRBVar[] inputs = new GRBVar[] { v.entities[i].activeRows[r], v.entities[i].activeRows[r + 1],
                            increasing };
                    ctx.model.addGenConstrAnd(bothActive_increasing, inputs, "genAND_" + i + "_" + r);

                    // If increasing=1 and active rows j, j+1 -> enforce rowBounds[r][1] <=
                    // rowBounds[r+1][1]
                    ctx.model.addGenConstrIndicator(bothActive_increasing, 1, diff,
                            GRB.LESS_EQUAL, 0.0, "monoInc_" + i + "_" + r);

                    GRBVar bothActive_not_increasing = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            "active_rows_" + r + "_" + (r + 1) + "_ent_" + i);
                    inputs = new GRBVar[] { v.entities[i].activeRows[r], v.entities[i].activeRows[r + 1],
                            notInc };
                    ctx.model.addGenConstrAnd(bothActive_not_increasing, inputs, "genAND_" + i + "_" + r);
                    // If increasing=0 -> enforce rowBounds[r][1] >= rowBounds[r+1][1]
                    ctx.model.addGenConstrIndicator(bothActive_not_increasing, 1, diff,
                            GRB.GREATER_EQUAL, 0.0, "monoDec_" + i + "_" + r);
                }
            }
        }
    }
}

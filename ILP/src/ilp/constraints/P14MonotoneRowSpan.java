package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBVar;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

public class P14MonotoneRowSpan implements ConstraintModule {
    private double non_decreasing = 2.0;

    public P14MonotoneRowSpan(double non_decr) {
        super();
        this.non_decreasing = non_decr;
    }

    public P14MonotoneRowSpan() {
        super();
    }

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only add this constraint for polygon solutions
            int nEntities = ctx.entityIds.size();
            // Move this variables inside the first for loop if you want to allow different
            // entities to be monotonic in different direction
            GRBVar nonDecrBool = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "span_non-decreasing");

            // if you want to set the direction yourself:
            if (non_decreasing == 1.0 || non_decreasing == 0.0) {
                ctx.model.addConstr(nonDecrBool, GRB.EQUAL, non_decreasing,
                        "row_span does not decrease for all entities");
            }

            GRBVar nonIncrBool = ctx.model.addVar(0, 1, 0, GRB.BINARY, "notInc");

            // notInc = 1 - increasing
            GRBLinExpr notExpr = new GRBLinExpr();
            notExpr.addTerm(1.0, nonDecrBool);
            notExpr.addTerm(1.0, nonIncrBool);
            ctx.model.addConstr(notExpr, GRB.EQUAL, 1.0, "not_increasing");

            for (int i = 0; i < nEntities; i++) {
                for (int j = 1; j <= ctx.dimensions; j++) {

                    GRBVar bothActive = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            "active_rows_" + j + "_" + (j - 1) + "_ent_" + i);

                    GRBVar[] inputs = new GRBVar[] { v.entities[i].activeRows[j - 1], v.entities[i].activeRows[j] };
                    ctx.model.addGenConstrAnd(bothActive, inputs, "genAND_" + i + "_" + j);

                    // if this and previous rows are active and (increasing), then span[j] >=
                    // span[j-1]
                    GRBVar bothActive_increasing = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            "increasing_active_" + j + "_" + i);
                    ctx.model.addGenConstrAnd(bothActive_increasing, new GRBVar[] { nonDecrBool, bothActive }, "andGen");

                    GRBLinExpr expr = new GRBLinExpr();
                    expr.addTerm(1.0, v.entities[i].rowSpans[j]);
                    expr.addTerm(-1.0, v.entities[i].rowSpans[j - 1]);
                    ctx.model.addGenConstrIndicator(bothActive_increasing, 1, expr, GRB.GREATER_EQUAL, 0.0, "ind");

                    // if this and previous rows are active and (not increasing), then span[j] <=
                    // span[j-1]

                    GRBVar bothActive_notincreasing = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            "notincreasing_active_" + j + "_" + i);
                    ctx.model.addGenConstrAnd(bothActive_notincreasing, new GRBVar[] { nonIncrBool, bothActive }, "andGen");

                    GRBLinExpr expr1 = new GRBLinExpr();
                    expr1.addTerm(1.0, v.entities[i].rowSpans[j]);
                    expr1.addTerm(-1.0, v.entities[i].rowSpans[j - 1]);
                    ctx.model.addGenConstrIndicator(bothActive_notincreasing, 1, expr1, GRB.LESS_EQUAL, 0.0, "ind");
                }
            }
        }
    }
}

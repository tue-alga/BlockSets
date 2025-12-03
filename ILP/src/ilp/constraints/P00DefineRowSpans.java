package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

public class P00DefineRowSpans implements ConstraintModule {
    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only add this constraint for polygon solutions
            int nEntities = ctx.entityIds.size();
            for (int i = 0; i < nEntities; i++) {
                for (int j = 0; j <= ctx.dimensions; j++) {
                    GRBLinExpr span_j_expr1 = new GRBLinExpr();
                    span_j_expr1.addTerm(1.0, v.entities[i].rowSpans[j]);
                    span_j_expr1.addTerm(-1.0, v.entities[i].rowBounds[j][1]);
                    span_j_expr1.addTerm(1.0, v.entities[i].rowBounds[j][0]);
                    ctx.model.addGenConstrIndicator(v.entities[i].activeRows[j], 1,
                            span_j_expr1, GRB.EQUAL, 1.0,
                            "span_if_ej_1");

                    // If e_j == 0 -> span_j = 0
                    GRBLinExpr span_j_expr0 = new GRBLinExpr();
                    span_j_expr0.addTerm(1.0, v.entities[i].rowSpans[j]);
                    ctx.model.addGenConstrIndicator(v.entities[i].activeRows[j], 0,
                            span_j_expr0, GRB.EQUAL, 0.0,
                            "span_if_ej_0");

                }
            }
        }
    }
}

package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBVar;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

public class P3VerticalConvexityWrong implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only add this constraint for polygon solutions
            int nEntities = ctx.entityIds.size();
            for (int i = 0; i < nEntities; i++) {
                for (int j = 1; j < ctx.dimensions; j++) {

                    // ------------------------ left side ------------------------ //

                    GRBVar b1 = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "convex_left_nextrow_b1");
                    GRBLinExpr expr = new GRBLinExpr();
                    expr.addTerm(1.0, v.entities[i].rowBounds[j + 1][0]);
                    expr.addTerm(-1.0, v.entities[i].rowBounds[j][0]);

                    ctx.model.addGenConstrIndicator(b1, 1, expr,
                            GRB.GREATER_EQUAL, 0.0, "e_" + i + "_row_" + j + "_b1=1_implies_nextRowStartsLater");

                    GRBVar b2 = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "convex_left_nextrow_b2");
                    GRBLinExpr expr2 = new GRBLinExpr();
                    expr2.addTerm(1.0, v.entities[i].rowBounds[j - 1][0]);
                    expr2.addTerm(-1.0, v.entities[i].rowBounds[j][0]);

                    ctx.model.addGenConstrIndicator(b2, 1, expr2,
                            GRB.GREATER_EQUAL, 0.0, "e_" + i + "_row_" + j + "b1=1_implies_prevRowStartsLater");

                    GRBLinExpr orExpr = new GRBLinExpr();
                    orExpr.addTerm(1.0, b1);
                    orExpr.addTerm(1.0, b2);
                    ctx.model.addConstr(orExpr, GRB.GREATER_EQUAL, 1, "either_previous_or_next_row_start_later");

                    // ------------------------ right side ------------------------ //

                    GRBVar b3 = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "convex_left_nextrow_b1");
                    GRBLinExpr expr3 = new GRBLinExpr();
                    expr3.addTerm(1.0, v.entities[i].rowBounds[j + 1][1]);
                    expr3.addTerm(-1.0, v.entities[i].rowBounds[j][1]);

                    ctx.model.addGenConstrIndicator(b3, 1, expr3,
                            GRB.LESS_EQUAL, 0.0, "e_" + i + "_row_" + j + "_b3=1_implies_nextRowEndsEarlier");

                    GRBVar b4 = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "convex_left_nextrow_b2");
                    GRBLinExpr expr4 = new GRBLinExpr();
                    expr4.addTerm(1.0, v.entities[i].rowBounds[j - 1][1]);
                    expr4.addTerm(-1.0, v.entities[i].rowBounds[j][1]);

                    ctx.model.addGenConstrIndicator(b4, 1, expr4,
                            GRB.LESS_EQUAL, 0.0, "e_" + i + "_row_" + j + "b4=1_implies_prevRowEndsEarlier");

                    GRBLinExpr orExpr2 = new GRBLinExpr();
                    orExpr2.addTerm(1.0, b3);
                    orExpr2.addTerm(1.0, b4);
                    ctx.model.addConstr(orExpr2, GRB.GREATER_EQUAL, 1, "either_previous_or_next_row_start_later");
                }
            }
        }
    }
}

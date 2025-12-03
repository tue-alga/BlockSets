package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;
import ilp.variables.VarsRectangles;

// Ensure squareness, difference between max width and max height is minimized
// (H10)
public class H10Squareness implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if (ctx.v instanceof VarsRectangles rect) {
            optimizeForSquareness(ctx, rect);
        } else if (ctx.v instanceof VarsPolygons poly) {
            optimizeForSquareness(ctx, poly);
        }
    }

    private void optimizeForSquareness(ModelContext ctx, VarsRectangles v) throws GRBException {
        GRBLinExpr positiveDiff = new GRBLinExpr();
            positiveDiff.addTerm(1.0, v.diff);
            positiveDiff.addTerm(-1.0, v.maxWidth);
            positiveDiff.addTerm(1.0, v.maxHeight);
            ctx.model.addConstr(positiveDiff, GRB.GREATER_EQUAL, 0, "H10_+diff");

            GRBLinExpr negativeDiff = new GRBLinExpr();
            negativeDiff.addTerm(1.0, v.diff);
            negativeDiff.addTerm(1.0, v.maxWidth);
            negativeDiff.addTerm(-1.0, v.maxHeight);
            ctx.model.addConstr(negativeDiff, GRB.GREATER_EQUAL, 0, "H10_-diff");
    }

    private void optimizeForSquareness(ModelContext ctx, VarsPolygons v) throws GRBException {
        GRBLinExpr positiveDiff = new GRBLinExpr();
            positiveDiff.addTerm(1.0, v.diff);
            positiveDiff.addTerm(-1.0, v.maxWidth);
            positiveDiff.addTerm(1.0, v.maxHeight);
            ctx.model.addConstr(positiveDiff, GRB.GREATER_EQUAL, 0, "H10_+diff");

            GRBLinExpr negativeDiff = new GRBLinExpr();
            negativeDiff.addTerm(1.0, v.diff);
            negativeDiff.addTerm(1.0, v.maxWidth);
            negativeDiff.addTerm(-1.0, v.maxHeight);
            ctx.model.addConstr(negativeDiff, GRB.GREATER_EQUAL, 0, "H10_-diff");
    }
}

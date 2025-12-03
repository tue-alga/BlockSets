package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;
import ilp.variables.VarsRectangles;

// Bound the sum of the width and height
public class C3TotalLayoutSize implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if (ctx.v instanceof VarsRectangles rect) {
            boundTotalSize(ctx, rect);
        } else if (ctx.v instanceof VarsPolygons poly) {
            boundTotalSize(ctx, poly);
        }
    }

    private void boundTotalSize(ModelContext ctx, VarsRectangles v) throws GRBException {
        // Sum of width height constraint
        GRBLinExpr totalSize = new GRBLinExpr();
        totalSize.addTerm(1.0, v.maxHeight);
        totalSize.addTerm(1.0, v.maxWidth);
        ctx.model.addConstr(totalSize, GRB.LESS_EQUAL, ctx.maxSizeSum, "total_layout_size");
    }

    private void boundTotalSize(ModelContext ctx, VarsPolygons v) throws GRBException {
        // Sum of width height constraint
        GRBLinExpr totalSize = new GRBLinExpr();
        totalSize.addTerm(1.0, v.maxHeight);
        totalSize.addTerm(1.0, v.maxWidth);
        ctx.model.addConstr(totalSize, GRB.LESS_EQUAL, ctx.maxSizeSum, "total_layout_size");
    }
}

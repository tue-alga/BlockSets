package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

// This constraint forces the rows to be subsets of the rows above.
public class P13aNestedRowBoundsNonIncreasing implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only add this constraint for polygon solutions
            for (int i = 0; i < ctx.entityIds.size(); i++) {
                for (int r = 0; r < ctx.dimensions; r++) {
                    GRBLinExpr leftDiff = new GRBLinExpr();
                    leftDiff.addTerm(1.0, v.entities[i].rowBounds[r][0]);
                    leftDiff.addTerm(-1.0, v.entities[i].rowBounds[r + 1][0]);

                    //enforce rowBounds[r][0] <= rowBounds[r+1][0]
                    ctx.model.addGenConstrIndicator(v.entities[i].activeRows[r+1], 1, leftDiff, GRB.LESS_EQUAL, 0.0, "monoDec_" + i + "_" + r);

                    GRBLinExpr rightDiff = new GRBLinExpr();
                    rightDiff.addTerm(1.0, v.entities[i].rowBounds[r][1]);
                    rightDiff.addTerm(-1.0, v.entities[i].rowBounds[r + 1][1]);

                    // enforce rowBounds[r][1] <= rowBounds[r+1][1]
                    ctx.model.addGenConstrIndicator(v.entities[i].activeRows[r], 1, rightDiff, GRB.GREATER_EQUAL, 0.0, "monoDec_" + i + "_" + r);
                }
            }
        }
    }
}

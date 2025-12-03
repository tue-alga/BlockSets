package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

public class P2ConnectedEntityRows implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only add this constraint for polygon solutions
            int nEntities = ctx.entityIds.size();

            // consecutive rows should have an overlap in their x-coordinates
            for (int i = 0; i < nEntities; i++) {
                for (int j = 0; j < ctx.dimensions; j++) {

                    // end of row j is after the start of row j+1
                    GRBLinExpr leftOverlap = new GRBLinExpr();
                    leftOverlap.addTerm(1.0, v.entities[i].rowBounds[j][1]);
                    leftOverlap.addTerm(-1.0, v.entities[i].rowBounds[j + 1][0]);

                    ctx.model.addGenConstrIndicator(v.entities[i].activeRows[j], 1, leftOverlap,
                            GRB.GREATER_EQUAL, 0.0, "e_" + i + "_row_" + j + "_left_overlap");

                    // start of row j is before the end of row j+1
                    GRBLinExpr rightOverlap = new GRBLinExpr();
                    rightOverlap.addTerm(1.0, v.entities[i].rowBounds[j + 1][1]);
                    rightOverlap.addTerm(-1.0, v.entities[i].rowBounds[j][0]);

                    // TODO: test
                    ctx.model.addGenConstrIndicator(v.entities[i].activeRows[j+1], 1, rightOverlap,
                            GRB.GREATER_EQUAL, 0.0, "e_" + i + "_row_" + j + "_right_overlap");
                }
            }
        }
    }
}
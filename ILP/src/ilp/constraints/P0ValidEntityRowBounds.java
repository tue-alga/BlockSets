package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

public class P0ValidEntityRowBounds implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only add this constraint for polygon solutions
            int nEntities = ctx.entityIds.size();
            for (int i = 0; i < nEntities; i++) {
                for (int j = 0; j <= ctx.dimensions; j++) {
                    // For each row of the entities, the end of the row is after the start of the
                    // row.
                    GRBLinExpr differenceOfCoord = new GRBLinExpr();
                    differenceOfCoord.addTerm(1.0, v.entities[i].rowBounds[j][1]);
                    differenceOfCoord.addTerm(-1.0, v.entities[i].rowBounds[j][0]);

                    ctx.model.addGenConstrIndicator(v.entities[i].activeRows[j], 1, differenceOfCoord,
                            GRB.GREATER_EQUAL, 0.0, "e_" + i + "_row_" + j + "_well_defined");

                    // For each non active row, row bounds are always 0
                    GRBLinExpr start = new GRBLinExpr();
                    GRBLinExpr end = new GRBLinExpr();
                    start.addTerm(1.0, v.entities[i].rowBounds[j][0]);
                    end.addTerm(1.0, v.entities[i].rowBounds[j][1]);
                    ctx.model.addGenConstrIndicator(v.entities[i].activeRows[j], 0, start,
                            GRB.EQUAL, 0.0, "e_" + i + "_row_" + j + "_well_defined");
                    ctx.model.addGenConstrIndicator(v.entities[i].activeRows[j], 0, end,
                            GRB.EQUAL, 0.0, "e_" + i + "_row_" + j + "_well_defined");

                }
            }
        }
    }
}

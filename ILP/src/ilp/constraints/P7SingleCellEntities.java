package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

public class P7SingleCellEntities implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only add this constraint for polygon solutions
            int nEntities = ctx.entityIds.size();
            for (int i = 0; i < nEntities; i++) {
                if (ctx.inst.entityIndToStatements.get(ctx.entityIds.get(i)).length == 1) {
                    GRBLinExpr nrActiveRows = new GRBLinExpr();

                    // if row is active then the size of the row is 1 cell (for entities with 1
                    // statement)
                    for (int row = 0; row <= ctx.dimensions; row++) {
                        nrActiveRows.addTerm(1.0, v.entities[i].activeRows[row]);

                        GRBLinExpr sizeOfRow = new GRBLinExpr();
                        sizeOfRow.addTerm(1.0, v.entities[i].rowBounds[row][1]);
                        sizeOfRow.addTerm(-1.0, v.entities[i].rowBounds[row][0]);
                        ctx.model.addGenConstrIndicator(v.entities[i].activeRows[row], 1, sizeOfRow, GRB.EQUAL, 0,
                                "single_cell_span");
                    }

                    ctx.model.addConstr(nrActiveRows, GRB.EQUAL, 1, "one_active_row");
                }
            }
        }
    }
}

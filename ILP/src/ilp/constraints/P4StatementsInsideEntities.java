package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

public class P4StatementsInsideEntities implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only add this constraint for polygon solutions
            int nEntities = ctx.entityIds.size();
            // Positioning statements inside entities (H1)
            for (int i = 0; i < nEntities; i++) {
                int entityId = ctx.entityIds.get(i);
                int[] statementsOfEntity = ctx.inst.entityIndToStatements.get(entityId);
                for (int st = 0; st < statementsOfEntity.length; st++) {
                    int statementIndex = ctx.statementIdToIdx.get(statementsOfEntity[st]);
                    for (int j = 0; j <= ctx.dimensions; j++) {

                        // if s_y = j, then e_j = 1
                        GRBLinExpr e_j = new GRBLinExpr();
                        e_j.addTerm(1.0, v.entities[i].activeRows[j]);
                        ctx.model.addGenConstrIndicator(v.statementIsOnRow[statementIndex][j], 1, e_j, GRB.EQUAL, 1,
                                "activate_entity_" + j);

                        // if s_y = j, then s_x is between the start and end of entity e, row j
                        GRBLinExpr s_x_e_j0 = new GRBLinExpr();
                        s_x_e_j0.addTerm(1.0, v.statementCoordinates[statementIndex][0]);
                        s_x_e_j0.addTerm(-1.0, v.entities[i].rowBounds[j][0]);
                        ctx.model.addGenConstrIndicator(v.statementIsOnRow[statementIndex][j], 1, s_x_e_j0,
                                GRB.GREATER_EQUAL, 0.0, "s_x_ge_ej0");

                        GRBLinExpr s_x_e_j1 = new GRBLinExpr();
                        s_x_e_j1.addTerm(1.0, v.statementCoordinates[statementIndex][0]);
                        s_x_e_j1.addTerm(-1.0, v.entities[i].rowBounds[j][1]);
                        ctx.model.addGenConstrIndicator(v.statementIsOnRow[statementIndex][j], 1, s_x_e_j1,
                                GRB.LESS_EQUAL, 0.0, "s_x_le_ej1");
                    }
                }
            }
        }
    }
}

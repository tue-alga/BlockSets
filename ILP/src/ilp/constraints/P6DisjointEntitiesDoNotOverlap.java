package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBVar;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

public class P6DisjointEntitiesDoNotOverlap implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only add this constraint for polygon solutions
            int nEntities = ctx.entityIds.size();

            for (int i = 0; i < nEntities; i++) {
                for (int j = i + 1; j < nEntities; j++) {
                    int[] statementsOfEntity1 = ctx.inst.entityIndToStatements.get(ctx.entityIds.get(i));
                    int[] statementsOfEntity2 = ctx.inst.entityIndToStatements.get(ctx.entityIds.get(j));
                    boolean overlap = false;
                    for (int k = 0; k < statementsOfEntity1.length; k++) {
                        for (int l = 0; l < statementsOfEntity2.length; l++) {
                            if (statementsOfEntity1[k] == statementsOfEntity2[l]) {
                                overlap = true;
                                break;
                            }
                        }
                    }

                    if (!overlap) {
                        for (int row = 0; row <= ctx.dimensions; row++) {

                            // Different rows
                            GRBVar notBothOnThisRow = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                    "not both on this row, as already mentioned");

                            GRBLinExpr exprForVarAbove = new GRBLinExpr();
                            exprForVarAbove.addTerm(1.0, v.entities[i].activeRows[row]);
                            exprForVarAbove.addTerm(1.0, v.entities[j].activeRows[row]);

                            ctx.model.addGenConstrIndicator(notBothOnThisRow, 1, exprForVarAbove,
                                    GRB.LESS_EQUAL, 1.0,
                                    "e_" + i + "_row_" + j + "_notBothOnThisRow=1_implies_ei_and_ej_not_on_row");

                            // Entity i after entity j
                            GRBVar afterEntityJ = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "after_that_entity");
                            GRBLinExpr exprForVarAbove2 = new GRBLinExpr();
                            exprForVarAbove2.addTerm(1.0, v.entities[i].rowBounds[row][0]);
                            exprForVarAbove2.addTerm(-1.0, v.entities[j].rowBounds[row][1]);
                            ctx.model.addGenConstrIndicator(afterEntityJ, 1, exprForVarAbove2,
                                    GRB.GREATER_EQUAL, 1.0,
                                    "e_" + i + "_row_" + j + "_afterEntityJ=1_implies_ei_after_ej");

                            // Entity i before entity j
                            GRBVar beforeEntityJ = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "before_that_entity");
                            GRBLinExpr exprForVarAbove3 = new GRBLinExpr();
                            exprForVarAbove3.addTerm(1.0, v.entities[i].rowBounds[row][1]);
                            exprForVarAbove3.addTerm(-1.0, v.entities[j].rowBounds[row][0]);
                            ctx.model.addGenConstrIndicator(beforeEntityJ, 1, exprForVarAbove3,
                                    GRB.LESS_EQUAL, -1.0,
                                    "e_" + i + "_row_" + j + "_beforeEntityJ=1_implies_ei_before_ej");

                            // at least one of the above should hold
                            GRBLinExpr orExpr = new GRBLinExpr();
                            orExpr.addTerm(1.0, beforeEntityJ);
                            orExpr.addTerm(1.0, afterEntityJ);
                            orExpr.addTerm(1.0, notBothOnThisRow);
                            ctx.model.addConstr(orExpr, GRB.GREATER_EQUAL, 1, "entities do not overlap");
                        }
                    }
                }
            }
        }
    }
}

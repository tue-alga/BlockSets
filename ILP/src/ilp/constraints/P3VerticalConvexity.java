package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBVar;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

public class P3VerticalConvexity implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only add this constraint for polygon solutions
            int nEntities = ctx.entityIds.size();
            for (int i = 0; i < nEntities; i++) {
                for (int s = 0; s < ctx.dimensions - 1; s++) {
                    for (int f = s + 2; f <= ctx.dimensions; f++) {
                        for (int j = s + 1; j < f; j++) {
                            GRBVar zStart = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "middleStartsBeforeFirstOrLast");
                            // xStart[k] ≤ xStart[i] + M*(1 - z + (3 - active[i] - active[k] - active[j]))
                            GRBLinExpr middleStartsBeforeFirst = new GRBLinExpr();
                            middleStartsBeforeFirst.addTerm(1.0, v.entities[i].rowBounds[s][0]);
                            middleStartsBeforeFirst.addTerm(-1.0, v.entities[i].rowBounds[j][0]);
                            middleStartsBeforeFirst.addConstant(ctx.bigM);
                            middleStartsBeforeFirst.addTerm(-ctx.bigM, zStart);
                            middleStartsBeforeFirst.addConstant(3 * ctx.bigM);
                            middleStartsBeforeFirst.addTerm(-ctx.bigM, v.entities[i].activeRows[s]);
                            middleStartsBeforeFirst.addTerm(-ctx.bigM, v.entities[i].activeRows[j]);
                            middleStartsBeforeFirst.addTerm(-ctx.bigM, v.entities[i].activeRows[f]);

                            ctx.model.addConstr(middleStartsBeforeFirst, GRB.GREATER_EQUAL, 0, "middleCanStartBeforeFirst");

                            // xStart[k] ≤ xStart[j] + M*(z + (3 - active[i] - active[k] - active[j]))
                            GRBLinExpr middleStartsBeforeLast = new GRBLinExpr();
                            middleStartsBeforeLast.addTerm(1.0, v.entities[i].rowBounds[f][0]);
                            middleStartsBeforeLast.addTerm(-1.0, v.entities[i].rowBounds[j][0]);
                            middleStartsBeforeLast.addTerm(ctx.bigM, zStart);
                            middleStartsBeforeLast.addConstant(3 * ctx.bigM);
                            middleStartsBeforeLast.addTerm(-ctx.bigM, v.entities[i].activeRows[s]);
                            middleStartsBeforeLast.addTerm(-ctx.bigM, v.entities[i].activeRows[j]);
                            middleStartsBeforeLast.addTerm(-ctx.bigM, v.entities[i].activeRows[f]);

                            ctx.model.addConstr(middleStartsBeforeLast, GRB.GREATER_EQUAL, 0, "middleCanStartBeforeLast");

                            GRBVar zEnd = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "middleEndsAfterFirstOrLast");

                            // xEnd[k] ≥ xEnd[i] - M*(1 - z + (3 - active[i] - active[k] - active[j]))
                            GRBLinExpr middleEndsAfterFirst = new GRBLinExpr();
                            middleEndsAfterFirst.addTerm(1.0, v.entities[i].rowBounds[s][1]);
                            middleEndsAfterFirst.addTerm(-1.0, v.entities[i].rowBounds[j][1]);
                            middleEndsAfterFirst.addConstant(-ctx.bigM);
                            middleEndsAfterFirst.addTerm(ctx.bigM, zEnd);
                            middleEndsAfterFirst.addConstant(-(3 * ctx.bigM));
                            middleEndsAfterFirst.addTerm(ctx.bigM, v.entities[i].activeRows[s]);
                            middleEndsAfterFirst.addTerm(ctx.bigM, v.entities[i].activeRows[j]);
                            middleEndsAfterFirst.addTerm(ctx.bigM, v.entities[i].activeRows[f]);

                            ctx.model.addConstr(middleEndsAfterFirst, GRB.LESS_EQUAL, 0, "middleCanEndAfterFirst");

                            // xEnd[k] ≥ xEnd[j] - M*(z + (3 - active[i] - active[k] - active[j]))
                            GRBLinExpr middleEndsAfterLast = new GRBLinExpr();
                            middleEndsAfterLast.addTerm(1.0, v.entities[i].rowBounds[f][1]);
                            middleEndsAfterLast.addTerm(-1.0, v.entities[i].rowBounds[j][1]);
                            middleEndsAfterLast.addTerm(-ctx.bigM, zEnd);
                            middleEndsAfterLast.addConstant(-(3 * ctx.bigM));
                            middleEndsAfterLast.addTerm(ctx.bigM, v.entities[i].activeRows[s]);
                            middleEndsAfterLast.addTerm(ctx.bigM, v.entities[i].activeRows[j]);
                            middleEndsAfterLast.addTerm(ctx.bigM, v.entities[i].activeRows[f]);

                            ctx.model.addConstr(middleEndsAfterLast, GRB.LESS_EQUAL, 0, "middleCanEndAfterLast");
                        }
                    }
                }
            }
        }
    }
}

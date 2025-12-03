package ilp.objective;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBVar;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

public class PolygonAreaDimensionsComplexity implements ObjectiveModule {

        @Override
        public void apply(ModelContext ctx) throws GRBException {
                if ((ctx.v instanceof VarsPolygons v)) { // only use this objective function for polygon solutions
                        int nEntities = ctx.entityIds.size();
                        GRBLinExpr objective = new GRBLinExpr();

                        // Opt2: Minimize dimensions of solution
                        objective.addTerm(1.0, v.maxWidth);
                        objective.addTerm(1.0, v.maxHeight);
                        PolygonAreaDimensionsComplexity.addComplexity(ctx, objective);

                        for (int i = 0; i < nEntities; i++) {
                                for (int j = 0; j <= ctx.dimensions; j++) {
                                        // Add span_j to objective
                                        objective.addTerm(1.0, v.entities[i].rowSpans[j]);
                                }
                        }
                        ctx.model.setObjective(objective, GRB.MINIMIZE);
                }
        }

        private static void addComplexity(ModelContext ctx, GRBLinExpr objective) throws GRBException {
                if ((ctx.v instanceof VarsPolygons v)) { // only include this for polygon solutions
                        int nEntities = ctx.entityIds.size();

                        for (int i = 0; i < nEntities; i++) {
                                for (int j = 0; j < ctx.dimensions; j++) {
                                        // b_active_consecutive: e_j and e_(j+1) are active
                                        GRBVar b_active_consecutive = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                                        "b=1_implies_rows_" + j + "_" + j + 1 + "_are_active");

                                        // b_active_consecutive => e_j + e_{j+1} = 2
                                        GRBLinExpr active_expr = new GRBLinExpr();
                                        active_expr.addTerm(1.0, v.entities[i].activeRows[j]);
                                        active_expr.addTerm(1.0, v.entities[i].activeRows[j + 1]);
                                        ctx.model.addGenConstrIndicator(b_active_consecutive, 1, active_expr, GRB.EQUAL,
                                                        2,
                                                        "rows_" + j + "_" + j + 1 + "_are_active");

                                        // e_j + e_{j+1} = 2 => b_active_consecutive
                                        // e_j + e_{j+1} - b_active_consecutive <= 1 // for some reason this works
                                        GRBLinExpr otherSide_expr = new GRBLinExpr();
                                        otherSide_expr.addTerm(1.0, v.entities[i].activeRows[j]);
                                        otherSide_expr.addTerm(1.0, v.entities[i].activeRows[j + 1]);
                                        otherSide_expr.addTerm(-1.0, b_active_consecutive);
                                        ctx.model.addConstr(otherSide_expr, GRB.LESS_EQUAL, 1.0, "quantum logic");

                                        // different size of consecutive rows (left side)
                                        GRBVar diff_InLeftSide = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                                        "diff_in_left_size");

                                        GRBVar lesser = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                                        "less_in_left_size");
                                        GRBVar larger = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                                        "larger_in_left_size");

                                        GRBLinExpr difference = new GRBLinExpr();
                                        difference.addTerm(1.0, v.entities[i].rowBounds[j][0]);
                                        difference.addTerm(-1.0, v.entities[i].rowBounds[j + 1][0]);
                                        ctx.model.addGenConstrIndicator(lesser, 1, difference, GRB.LESS_EQUAL, -1.0,
                                                        "rows_" + j + "_" + j + 1 + "_negative_difference_left_side");
                                        ctx.model.addGenConstrIndicator(larger, 1, difference, GRB.GREATER_EQUAL, 1.0,
                                                        "rows_" + j + "_" + j + 1 + "_positive_difference_left_side");

                                        // diff_inLeftSide => e_{j,0} != e_{j+1,0}
                                        GRBLinExpr orExpr = new GRBLinExpr();
                                        orExpr.addTerm(1.0, larger);
                                        orExpr.addTerm(1.0, lesser);
                                        ctx.model.addGenConstrIndicator(diff_InLeftSide, 1, orExpr, GRB.GREATER_EQUAL,
                                                        1,
                                                        "rows_" + j + "_" + j + 1 + "_exists_difference_left_side");

                                        // diff_inLeftSide <= e_{j,0} != e_{j+1,0}
                                        // larger or lesser => diff_inLeftSide, i.e. diff_inLeftSide >= larger and
                                        // diff_inLeftSide >= lesser
                                        GRBLinExpr larger_diff = new GRBLinExpr();
                                        GRBLinExpr lesser_diff = new GRBLinExpr();

                                        larger_diff.addTerm(1.0, diff_InLeftSide);
                                        larger_diff.addTerm(-1.0, larger);

                                        lesser_diff.addTerm(1.0, diff_InLeftSide);
                                        lesser_diff.addTerm(-1.0, lesser);

                                        ctx.model.addConstr(larger_diff, GRB.GREATER_EQUAL, 0.0, "ran out of names");
                                        ctx.model.addConstr(lesser_diff, GRB.GREATER_EQUAL, 0.0, "ran out of names2");

                                        // different size of consecutive rows (right side)
                                        GRBVar diff_InRightSide = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                                        "diff_in_right_size");

                                        GRBVar lesser_right = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                                        "less_in_right_size");
                                        GRBVar larger_right = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                                        "larger_in_right_size");

                                        GRBLinExpr difference_right = new GRBLinExpr();
                                        difference_right.addTerm(1.0, v.entities[i].rowBounds[j][1]);
                                        difference_right.addTerm(-1.0, v.entities[i].rowBounds[j + 1][1]);
                                        ctx.model.addGenConstrIndicator(lesser_right, 1, difference_right,
                                                        GRB.LESS_EQUAL, -1.0,
                                                        "rows_" + j + "_" + j + 1 + "_negative_difference_right_side");
                                        ctx.model.addGenConstrIndicator(larger_right, 1, difference_right,
                                                        GRB.GREATER_EQUAL,
                                                        1.0,
                                                        "rows_" + j + "_" + j + 1 + "_positive_difference_right_side");

                                        // diff_inRightSide => e_{j,1} != e_{j+1,1}
                                        GRBLinExpr orExpr_right = new GRBLinExpr();
                                        orExpr_right.addTerm(1.0, larger_right);
                                        orExpr_right.addTerm(1.0, lesser_right);
                                        ctx.model.addGenConstrIndicator(diff_InRightSide, 1, orExpr_right,
                                                        GRB.GREATER_EQUAL, 1,
                                                        "rows_" + j + "_" + j + 1 + "_exists_difference_right_side");

                                        // diff_inRightSide <= e_{j,1} != e_{j+1,1}
                                        // larger_right or lesser_right => diff_inRightSide, i.e. diff_inRightSide >=
                                        // larger_right and diff_inRightSide >= lesser_right
                                        GRBLinExpr larger_diff_right = new GRBLinExpr();
                                        GRBLinExpr lesser_diff_right = new GRBLinExpr();

                                        larger_diff_right.addTerm(1.0, diff_InRightSide);
                                        larger_diff_right.addTerm(-1.0, larger_right);

                                        lesser_diff_right.addTerm(1.0, diff_InRightSide);
                                        lesser_diff_right.addTerm(-1.0, lesser_right);

                                        ctx.model.addConstr(larger_diff_right, GRB.GREATER_EQUAL, 0.0,
                                                        "ran out of names3");
                                        ctx.model.addConstr(lesser_diff_right, GRB.GREATER_EQUAL, 0.0,
                                                        "ran out of names4");

                                        // if b_active_consecutive (j, and j+1 are active), then sumComplexity =
                                        // diff_InLeftSide + diff_InRightSide
                                        GRBVar sumComplexity = ctx.model.addVar(0.0, 2.0, 0.0, GRB.CONTINUOUS,
                                                        "complexity_consecutive_rows_" + j);
                                        GRBLinExpr expression = new GRBLinExpr();
                                        expression.addTerm(1.0, sumComplexity);
                                        expression.addTerm(-1.0, diff_InLeftSide);
                                        expression.addTerm(-1.0, diff_InRightSide);
                                        ctx.model.addGenConstrIndicator(b_active_consecutive, 1, expression, GRB.EQUAL,
                                                        0,
                                                        "if_active_consecutive_rows_then_sumComplexity");

                                        GRBLinExpr sumCompl_expr = new GRBLinExpr();
                                        sumCompl_expr.addTerm(1.0, sumComplexity);
                                        ctx.model.addGenConstrIndicator(b_active_consecutive, 0, sumCompl_expr,
                                                        GRB.EQUAL, 0,
                                                        "if_active_consecutive_rows_then_sumComplexity");

                                        objective.addTerm(1.0, sumComplexity);
                                }
                        }
                }
        }

}

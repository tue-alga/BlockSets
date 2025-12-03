package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBVar;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;

public class P15MonotoneColumnSpan implements ConstraintModule {

    private double non_decreasing = 2.0;

    public P15MonotoneColumnSpan() {
        super();
    }

    public P15MonotoneColumnSpan(double non_decr) {
        this.non_decreasing = non_decr;
    }

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only add this constraint for polygon solutions

            // Move this variables inside the first for loop if you want to allow different
            // entities to be monotonic in different direction
            GRBVar nonDecrBool = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "span_non-decreasing");

            // if you want to set the direction yourself:
            if (non_decreasing == 1.0 || non_decreasing == 0.0) {
                ctx.model.addConstr(nonDecrBool, GRB.EQUAL, non_decreasing,
                        "col_span does not decrease for all entities");
            }

            GRBVar nonIncrBool = ctx.model.addVar(0, 1, 0, GRB.BINARY, "notInc");

            // notInc = 1 - increasing
            GRBLinExpr notExpr = new GRBLinExpr();
            notExpr.addTerm(1.0, nonDecrBool);
            notExpr.addTerm(1.0, nonIncrBool);
            ctx.model.addConstr(notExpr, GRB.EQUAL, 1.0, "not_increasing");

            for (int i = 0; i < ctx.entityIds.size(); i++) {
                for (int col = 0; col < ctx.dimensions; col++) {
                    GRBLinExpr colSpan = new GRBLinExpr();
                    GRBLinExpr colSpanNext = new GRBLinExpr();

                    for (int row = 0; row <= ctx.dimensions; row++) {

                        // Boolean that indicates whether column col is in the bounds of row "row" of
                        // the entity
                        GRBVar inRow = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                "col_" + col + "in_ent_" + i + "row_" + row);

                        // inRow = 1 => col is in bounds of row
                        // -- If inRow = 1, row.start <= col
                        GRBLinExpr expr1 = new GRBLinExpr();
                        expr1.addTerm(1.0, v.entities[i].rowBounds[row][0]);
                        expr1.addConstant(-col);
                        ctx.model.addGenConstrIndicator(inRow, 1, expr1, GRB.LESS_EQUAL, 0.0,
                                "inRow_left");

                        // -- If inRow = 1, col <= row.end
                        GRBLinExpr expr2 = new GRBLinExpr();
                        expr2.addConstant(col);
                        expr2.addTerm(-1.0, v.entities[i].rowBounds[row][1]);
                        ctx.model.addGenConstrIndicator(inRow, 1, expr2, GRB.LESS_EQUAL, 0.0,
                                "inRow_right");

                        // Other direction (<=) with contraposition
                        // inRow = 0 => col is not in bounds of the row
                        GRBVar b1 = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "left_of_row_b1");
                        GRBLinExpr left_of_row = new GRBLinExpr();
                        left_of_row.addTerm(1.0, v.entities[i].rowBounds[row][0]);
                        left_of_row.addConstant(-col);

                        ctx.model.addGenConstrIndicator(b1, 1, left_of_row,
                                GRB.GREATER_EQUAL, 1.0, "e_" + i + "_col_" + col + "_b1=1_implies_leftOfRow");

                        GRBVar b2 = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "right_of_row_b2");
                        GRBLinExpr right_of_row = new GRBLinExpr();
                        right_of_row.addTerm(1.0, v.entities[i].rowBounds[row][1]);
                        right_of_row.addConstant(-col);

                        ctx.model.addGenConstrIndicator(b2, 1, right_of_row,
                                GRB.LESS_EQUAL, -1.0, "e_" + i + "_row_" + row + "_b2=1_implies_rightOfRow");

                        // inRow = 0 => col is left of row or col is right of row
                        GRBLinExpr orExpr = new GRBLinExpr();
                        orExpr.addTerm(1.0, b1);
                        orExpr.addTerm(1.0, b2);
                        ctx.model.addGenConstrIndicator(inRow, 0, orExpr, GRB.GREATER_EQUAL,
                                1, "row_match_" + col);

                        // Boolean that indicates whether the row is active and the column is present in
                        // that row
                        GRBVar active_inRow = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                "active_inRow" + col + "_" + row);
                        ctx.model.addGenConstrAnd(active_inRow, new GRBVar[] { v.entities[i].activeRows[row], inRow },
                                "andGen");

                        // sum all such booleans to get span of column
                        colSpan.addTerm(1.0, active_inRow);

                        // ------------- Same for next column ----------------

                        GRBVar nextInRow = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                "col_" + (col+1) + "in_ent_" + i + "row_" + row);
                        // If inRow = 1, row.start <= col
                        GRBLinExpr expr3 = new GRBLinExpr();
                        expr3.addTerm(1.0, v.entities[i].rowBounds[row][0]);
                        expr3.addConstant(-(col + 1));
                        ctx.model.addGenConstrIndicator(nextInRow, 1, expr3, GRB.LESS_EQUAL, 0.0,
                                "inRow_left");

                        // If inRow = 1, col <= row.end
                        GRBLinExpr expr4 = new GRBLinExpr();
                        expr4.addConstant(col + 1);
                        expr4.addTerm(-1.0, v.entities[i].rowBounds[row][1]);
                        ctx.model.addGenConstrIndicator(nextInRow, 1, expr4, GRB.LESS_EQUAL, 0.0,
                                "inRow_right");

                        // Other direction (<=) with contraposition
                        // inRow = 0 => col is not in bounds of the row
                        GRBVar b3 = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "left_of_row_b1");
                        GRBLinExpr left_of_row_next = new GRBLinExpr();
                        left_of_row_next.addTerm(1.0, v.entities[i].rowBounds[row][0]);
                        left_of_row_next.addConstant(-(col + 1));

                        ctx.model.addGenConstrIndicator(b3, 1, left_of_row_next,
                                GRB.GREATER_EQUAL, 1.0, "e_" + i + "_col_" + (col + 1) + "_b1=1_implies_leftOfRow");

                        GRBVar b4 = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "right_of_row_b2");
                        GRBLinExpr right_of_row_next = new GRBLinExpr();
                        right_of_row_next.addTerm(1.0, v.entities[i].rowBounds[row][1]);
                        right_of_row_next.addConstant(-(col + 1));

                        ctx.model.addGenConstrIndicator(b4, 1, right_of_row_next,
                                GRB.LESS_EQUAL, -1.0, "e_" + i + "_row_" + row + "_b2=1_implies_rightOfRow");

                        // inRow = 0 => col is left of row or col is right of row
                        GRBLinExpr orExpr2 = new GRBLinExpr();
                        orExpr2.addTerm(1.0, b3);
                        orExpr2.addTerm(1.0, b4);
                        ctx.model.addGenConstrIndicator(nextInRow, 0, orExpr2, GRB.GREATER_EQUAL,
                                1, "row_match_" + col);

                        // Boolean that indicates both active and in row
                        GRBVar active_nextInRow = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                "active_inRow" + (col + 1) + "_" + row);
                        ctx.model.addGenConstrAnd(active_nextInRow,
                                new GRBVar[] { v.entities[i].activeRows[row], nextInRow },
                                "andGen");

                        colSpanNext.addTerm(1.0, active_nextInRow);
                    }

                    GRBVar activeCol = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "activeCol_" + col + "_ent_" + i);
                    GRBVar activeColNext = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            "activeCol_" + (col + 1) + "_ent_" + i);

                    // activeCol = 1 => colSpan >= 1 and activeCol = 0 => colSpan = 0
                    ctx.model.addGenConstrIndicator(activeCol, 1, colSpan, GRB.GREATER_EQUAL, 1, "col is active");
                    ctx.model.addGenConstrIndicator(activeCol, 0, colSpan, GRB.EQUAL, 0, "col is not active");
                    ctx.model.addGenConstrIndicator(activeColNext, 1, colSpanNext, GRB.GREATER_EQUAL, 1, "hi Neda");
                    ctx.model.addGenConstrIndicator(activeColNext, 0, colSpanNext, GRB.EQUAL, 0, "col is not active");

                    GRBVar bothActive_nondecreasing = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            "nondecreasing_active_" + col + "_" + i);
                    ctx.model.addGenConstrAnd(bothActive_nondecreasing,
                            new GRBVar[] { nonDecrBool, activeCol, activeColNext }, "andGen");

                    GRBVar bothActive_nonincreasing = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            "nonincreasing_active_" + col + "_" + i);
                    ctx.model.addGenConstrAnd(bothActive_nonincreasing,
                            new GRBVar[] { nonIncrBool, activeCol, activeColNext }, "andGen");

                    // colSpanNext - colSpan
                    GRBLinExpr colSpanDiff = new GRBLinExpr();
                    colSpanDiff.multAdd(1.0, colSpanNext);
                    colSpanDiff.multAdd(-1.0, colSpan);

                    ctx.model.addGenConstrIndicator(bothActive_nondecreasing, 1, colSpanDiff, GRB.GREATER_EQUAL, 0, "nameless");
                    ctx.model.addGenConstrIndicator(bothActive_nonincreasing, 1, colSpanDiff, GRB.LESS_EQUAL, 0, "nameless2");
                }
            }
        }
    }
}

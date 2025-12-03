package ilp.constraints;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBVar;
import ilp.ModelContext;
import ilp.variables.VarsPolygons;
import ilp.variables.VarsRectangles;

// Statements have distinct coordinates (C1)
public class C1StatementsDistinctCoordinates implements ConstraintModule {

    @Override
    public void add(ModelContext ctx) throws GRBException {
        if (ctx.v instanceof VarsRectangles rect) {
            distinctStatementPositions(ctx, rect);
        } else if (ctx.v instanceof VarsPolygons poly) {
            distinctStatementPositions(ctx, poly);
        }
    }

    private void distinctStatementPositions(ModelContext ctx, VarsRectangles v) throws GRBException {
        int nStatements = ctx.inst.numberOfStatements;
        final int M = ctx.bigM;

        for (int i = 0; i < nStatements; i++) {
            for (int j = i + 1; j < nStatements; j++) {
                GRBVar[] vars = new GRBVar[4];
                for (int k = 0; k < 4; k++) {
                    vars[k] = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "C1_" + i + "_" + j + "_" + k);
                }

                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(1.0, v.statementCoordinates[i][0]);
                expr.addTerm(-1.0, v.statementCoordinates[j][0]);
                expr.addTerm(M, vars[0]);
                ctx.model.addConstr(expr, GRB.GREATER_EQUAL, 1, "C1_" + i + "_" + j + "_x1");

                expr = new GRBLinExpr();
                expr.addTerm(1.0, v.statementCoordinates[i][0]);
                expr.addTerm(-1.0, v.statementCoordinates[j][0]);
                expr.addTerm(-1.0 * M, vars[1]);
                ctx.model.addConstr(expr, GRB.LESS_EQUAL, -1, "C1_" + i + "_" + j + "_x2");

                expr = new GRBLinExpr();
                expr.addTerm(1.0, v.statementCoordinates[i][1]);
                expr.addTerm(-1.0, v.statementCoordinates[j][1]);
                expr.addTerm(M, vars[2]);
                ctx.model.addConstr(expr, GRB.GREATER_EQUAL, 1, "C1_" + i + "_" + j + "_y1");

                expr = new GRBLinExpr();
                expr.addTerm(1.0, v.statementCoordinates[i][1]);
                expr.addTerm(-1.0, v.statementCoordinates[j][1]);
                expr.addTerm(-1.0 * M, vars[3]);
                ctx.model.addConstr(expr, GRB.LESS_EQUAL, -1, "C1_" + i + "_" + j + "_y2");

                expr = new GRBLinExpr();
                expr.addTerm(1.0, vars[0]);
                expr.addTerm(1.0, vars[1]);
                expr.addTerm(1.0, vars[2]);
                expr.addTerm(1.0, vars[3]);
                ctx.model.addConstr(expr, GRB.LESS_EQUAL, 3.0, "C1_" + i + "_" + j + "_sum");
            }
        }
    }

    private void distinctStatementPositions(ModelContext ctx, VarsPolygons v) throws GRBException {
        int nStatements = ctx.inst.numberOfStatements;
        final int M = ctx.bigM;

        for (int i = 0; i < nStatements; i++) {
            for (int j = i + 1; j < nStatements; j++) {
                GRBVar[] vars = new GRBVar[4];
                for (int k = 0; k < 4; k++) {
                    vars[k] = ctx.model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "C1_" + i + "_" + j + "_" + k);
                }

                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(1.0, v.statementCoordinates[i][0]);
                expr.addTerm(-1.0, v.statementCoordinates[j][0]);
                expr.addTerm(M, vars[0]);
                ctx.model.addConstr(expr, GRB.GREATER_EQUAL, 1, "C1_" + i + "_" + j + "_x1");

                expr = new GRBLinExpr();
                expr.addTerm(1.0, v.statementCoordinates[i][0]);
                expr.addTerm(-1.0, v.statementCoordinates[j][0]);
                expr.addTerm(-1.0 * M, vars[1]);
                ctx.model.addConstr(expr, GRB.LESS_EQUAL, -1, "C1_" + i + "_" + j + "_x2");

                expr = new GRBLinExpr();
                expr.addTerm(1.0, v.statementCoordinates[i][1]);
                expr.addTerm(-1.0, v.statementCoordinates[j][1]);
                expr.addTerm(M, vars[2]);
                ctx.model.addConstr(expr, GRB.GREATER_EQUAL, 1, "C1_" + i + "_" + j + "_y1");

                expr = new GRBLinExpr();
                expr.addTerm(1.0, v.statementCoordinates[i][1]);
                expr.addTerm(-1.0, v.statementCoordinates[j][1]);
                expr.addTerm(-1.0 * M, vars[3]);
                ctx.model.addConstr(expr, GRB.LESS_EQUAL, -1, "C1_" + i + "_" + j + "_y2");

                expr = new GRBLinExpr();
                expr.addTerm(1.0, vars[0]);
                expr.addTerm(1.0, vars[1]);
                expr.addTerm(1.0, vars[2]);
                expr.addTerm(1.0, vars[3]);
                ctx.model.addConstr(expr, GRB.LESS_EQUAL, 3.0, "C1_" + i + "_" + j + "_sum");
            }
        }
    }
}

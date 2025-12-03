package ilp;

import ilp.solvers.SolutionPositioner;
import ilp.variables.VarsPolygons;
import io.SolutionWriter;
import model.PolygonSolution;
import model.Solution;

import java.util.ArrayList;
import java.util.List;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBCallback;
import com.gurobi.gurobi.GRBException;

public class SaveSolutionCallback extends GRBCallback {

    private final ModelContext ctx;
    private final int solutionType;

    public SaveSolutionCallback(ModelContext ctx, int solutionType) {
        this.ctx = ctx;
        this.solutionType = solutionType;
    }

    @Override
    protected void callback() {
        try {
            if (where == GRB.CB_MIPSOL) {
                // A new feasible solution was just found

                System.out.println("Feasible solution found at time " + getDoubleInfo(GRB.CB_RUNTIME));

                // Extract the solution using your existing logic
                Solution sol;
                if (solutionType == 0) {
                    // sol = this.extractRectangleSolutionCallback(ctx);
                    // SolutionWriter.saveRectangleSolutionToFile((RectangleSolution) sol, "sol_callback.txt");
                } else {
//                    sol = this.extractPolygonSolutionCallback(ctx);
//                    SolutionWriter.saveMultipleToFile(SolutionPositioner.computeCompleteSolution(new ArrayList<>(List.of((Solution) sol)), allowRotation, allowReflection).solutions, sol.getW(), sol.getH(), "ILP/solutions/sol_callback.txt");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Solution extractPolygonSolutionCallback(ModelContext ctx)
            throws Exception, GRBException {

        if (!(ctx.v instanceof VarsPolygons v)) {
            throw new Exception("Incorrect solution type");
        }

        int nEntities = ctx.entityIds.size();
        int nStatements = ctx.statementIds.size();

        // Width and height
        int w = (int) this.getSolution(v.maxWidth);
        int h = (int) this.getSolution(v.maxHeight);

        // Statement coordinates
        int[][] statementCoordinates = new int[nStatements][2];

        for (int i = 0; i < nStatements; i++) {
            statementCoordinates[i][0] = (int) this.getSolution(v.statementCoordinates[i][0]);
            statementCoordinates[i][1] = (int) this.getSolution(v.statementCoordinates[i][1]);
        }

        // Entity row spans
        int[][][] entities = new int[nEntities][ctx.dimensions + 1][3];

        for (int i = 0; i < nEntities; i++) {
            for (int j = 0; j <= ctx.dimensions; j++) {

                entities[i][j][0] = (int) this.getSolution(v.entities[i].activeRows[j]);
                entities[i][j][1] = (int) this.getSolution(v.entities[i].rowBounds[j][0]);
                entities[i][j][2] = (int) this.getSolution(v.entities[i].rowBounds[j][1]);
            }
        }

        return new PolygonSolution(ctx.inst, w, h, ctx.entityIds, entities, statementCoordinates);
    }

}

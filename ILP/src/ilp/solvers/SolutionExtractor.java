package ilp.solvers;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;

import ilp.ModelContext;
import ilp.variables.VarsPolygons;
import ilp.variables.VarsRectangles;
import model.PolygonSolution;
import model.RectangleSolution;
import model.Solution;

public class SolutionExtractor {
    
    public static Solution extractRectangleSolution(ModelContext ctx) throws Exception, GRBException {
        if ((ctx.v instanceof VarsRectangles v)) { // only extracts rectangle solutions
            int nEntities = ctx.entityIds.size();
            int nStatements = ctx.statementIds.size();
            // Extract solution
            int w = (int) v.maxWidth.get(GRB.DoubleAttr.X);
            int h = (int) v.maxHeight.get(GRB.DoubleAttr.X);

            int[][] entityCoordinates = new int[nEntities][4];
            int[][] statementCoordinates = new int[nStatements][2];

            for (int i = 0; i < nEntities; i++) {
                entityCoordinates[i][0] = (int) v.entityCoordinates[i][0].get(GRB.DoubleAttr.X);
                entityCoordinates[i][1] = (int) v.entityCoordinates[i][1].get(GRB.DoubleAttr.X);
                entityCoordinates[i][2] = (int) v.entityCoordinates[i][2].get(GRB.DoubleAttr.X);
                entityCoordinates[i][3] = (int) v.entityCoordinates[i][3].get(GRB.DoubleAttr.X);
            }

            for (int i = 0; i < nStatements; i++) {
                statementCoordinates[i][0] = (int) v.statementCoordinates[i][0].get(GRB.DoubleAttr.X);
                statementCoordinates[i][1] = (int) v.statementCoordinates[i][1].get(GRB.DoubleAttr.X);
            }

            // Add solution to global list of solutions
            Solution newSolution = new RectangleSolution(ctx.inst, w, h, ctx.entityIds, entityCoordinates,
                    statementCoordinates);

            return newSolution;
        } else {
            throw new Exception("Incorrect solution type");
        }
    }

    public static Solution extractPolygonSolution(ModelContext ctx) throws Exception, GRBException {
        if ((ctx.v instanceof VarsPolygons v)) { // only extracts rectangle solutions
            int nEntities = ctx.entityIds.size();
            int nStatements = ctx.statementIds.size();
            // Extract solution
            int w = (int) v.maxWidth.get(GRB.DoubleAttr.X);
            int h = (int) v.maxHeight.get(GRB.DoubleAttr.X);

            int[][] statementCoordinates = new int[nStatements][2];

            // For each entity i and each grid row j:
            // entities[i][j][0] = whether row is active (entity is on this row)
            // entities[i][j][1] = beginning of entity on this row (if active)
            // entities[i][j][2] = end of entity on this row (if active)
            int[][][] entities = new int[nEntities][ctx.dimensions + 1][3];

            for (int i = 0; i < nEntities; i++) {
                for (int j = 0; j <= ctx.dimensions; j++) {
                    entities[i][j][0] = (int) v.entities[i].activeRows[j].get(GRB.DoubleAttr.X);
                    entities[i][j][1] = (int) v.entities[i].rowBounds[j][0].get(GRB.DoubleAttr.X);
                    entities[i][j][2] = (int) v.entities[i].rowBounds[j][1].get(GRB.DoubleAttr.X);
                }
            }

            for (int i = 0; i < nStatements; i++) {
                statementCoordinates[i][0] = (int) v.statementCoordinates[i][0].get(GRB.DoubleAttr.X);
                statementCoordinates[i][1] = (int) v.statementCoordinates[i][1].get(GRB.DoubleAttr.X);
            }

            // Add solution to global list of solutions
            Solution newSolution = new PolygonSolution(ctx.inst, w, h, ctx.entityIds, entities, statementCoordinates);

            return newSolution;
        } else {
            throw new Exception("Incorrect solution type");
        }
    }
}

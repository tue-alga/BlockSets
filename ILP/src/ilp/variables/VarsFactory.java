package ilp.variables;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBModel;
import com.gurobi.gurobi.GRBVar;

public final class VarsFactory {
    private VarsFactory() {
    }

    public static Vars create(GRBModel model,
            int nEntities,
            int nStatements,
            int coordLowerBound,
            int coordUpperBound, int modelType) throws GRBException {

        switch (modelType) {
            case 0:
                VarsRectangles v = VarsFactory.createRectangleVars(model, nEntities, nStatements, coordLowerBound,
                        coordUpperBound);
                return v;
            case 1:
                return createPolygonVars(model, nEntities, nStatements, coordLowerBound, coordUpperBound);
            default:
                System.out.println("Unidentified model type");
                return createRectangleVars(model, nEntities, nStatements, coordLowerBound, coordUpperBound);
        }
    }

    /** Creates all variables for an ILP using rectangles for Entities */
    public static VarsRectangles createRectangleVars(GRBModel model,
            int nEntities,
            int nStatements,
            int coordLowerBound,
            int coordUpperBound) throws GRBException {
        VarsRectangles v = new VarsRectangles();

        // statement coordinates: statementCoordinates[i][x/y]
        v.statementCoordinates = new GRBVar[nStatements][2];
        for (int i = 0; i < nStatements; i++) {
            v.statementCoordinates[i][0] = model.addVar(coordLowerBound, coordUpperBound, 0.0, GRB.INTEGER,
                    "s" + i + "_x");
            v.statementCoordinates[i][1] = model.addVar(coordLowerBound, coordUpperBound, 0.0, GRB.INTEGER,
                    "s" + i + "_y");
        }

        // entity coordinates: entityCoordinates[i][x1,y1,x2,y2]
        v.entityCoordinates = new GRBVar[nEntities][4];
        for (int i = 0; i < nEntities; i++) {
            for (int j = 0; j < 4; j++) {
                v.entityCoordinates[i][j] = model.addVar(coordLowerBound, coordUpperBound, 0.0, GRB.INTEGER,
                        "e" + i + "_" + (j % 2 == 0 ? "x" : "y") + (j / 2 == 0 ? "_t" : "_b"));
            }
        }

        // extra vars
        v.maxWidth = model.addVar(0.0, coordUpperBound + 1, 0.0, GRB.INTEGER, "maxWidth");
        v.maxHeight = model.addVar(0.0, coordUpperBound + 1, 0.0, GRB.INTEGER, "maxHeight");
        v.diff = model.addVar(0.0, coordUpperBound + 1, 0.0, GRB.INTEGER, "diff");

        return v;
    }

    /** Creates all variables for an ILP using polygons for Entities */
    public static VarsPolygons createPolygonVars(GRBModel model,
            int nEntities,
            int nStatements,
            int coordLowerBound,
            int coordUpperBound) throws GRBException {
        VarsPolygons v = new VarsPolygons();

        // statement coordinates: statementCoordinates[i][x/y]
        v.statementCoordinates = new GRBVar[nStatements][2];
        v.statementIsOnRow = new GRBVar[nStatements][coordUpperBound + 1];
        for (int i = 0; i < nStatements; i++) {
            v.statementCoordinates[i][0] = model.addVar(coordLowerBound, coordUpperBound, 0.0, GRB.INTEGER,
                    "s" + i + "_x");
            v.statementCoordinates[i][1] = model.addVar(coordLowerBound, coordUpperBound, 0.0, GRB.INTEGER,
                    "s" + i + "_y");

            for (int j = 0; j <= coordUpperBound; j++) {
                v.statementIsOnRow[i][j] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                        "statement_" + i + "_is_on_row_" + j);
            }
        }

        // Entities
        v.entities = new GurobiEntity[nEntities];
        for (int i = 0; i < nEntities; i++) {
            // For each row in the entity, a variable determining if it is used
            GRBVar[] rowBinaries = new GRBVar[coordUpperBound + 1];
            // For each row in the entity: start_x, end_x
            GRBVar[][] rowCoordinates = new GRBVar[coordUpperBound + 1][2];
            GRBVar[] rowSpans = new GRBVar[coordUpperBound + 1];
            for (int j = 0; j <= coordUpperBound; j++) {
                rowBinaries[j] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "e_" + i + "_" + j + "_active_row");

                rowCoordinates[j][0] = model.addVar(coordLowerBound, coordUpperBound, 0.0, GRB.INTEGER,
                        "e" + i + "_row_" + j + "start_x");
                rowCoordinates[j][1] = model.addVar(coordLowerBound, coordUpperBound, 0.0, GRB.INTEGER,
                        "e" + i + "_row_" + j + "end_x");

                rowSpans[j] = model.addVar(0, coordUpperBound + 1, 0.0, GRB.CONTINUOUS, "span_j");
            }
            v.entities[i] = new GurobiEntity(rowBinaries, rowCoordinates, rowSpans);
        }

        // extra vars
        v.maxWidth = model.addVar(0.0, coordUpperBound, 0.0, GRB.CONTINUOUS, "maxWidth");
        v.maxHeight = model.addVar(0.0, coordUpperBound, 0.0, GRB.CONTINUOUS, "maxHeight");
        v.diff = model.addVar(0.0, coordUpperBound, 0.0, GRB.CONTINUOUS, "diff");

        return v;
    }
}

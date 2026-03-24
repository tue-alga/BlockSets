package model;

import java.awt.Point;
import java.util.ArrayList;

public class PolygonSolution implements Solution {
    public StatementEntityInstance instance;
    public int w;
    public int h;
    public int startX = 0;
    public int startY = 0;
    ArrayList<Integer> entityIds;
    public int[][][] entities;
    public int[][] statementCoordinates;
    public ArrayList<Point> cells;

    public PolygonSolution(StatementEntityInstance inst, int w, int h, ArrayList<Integer> eIds, int[][][] entities,
            int[][] sCoords) {
        this.instance = inst;
        this.w = w;
        this.h = h;
        this.entityIds = eIds;
        this.entities = entities;
        this.statementCoordinates = sCoords;
        setCells();
    }
    
    @Override
    public StatementEntityInstance getInstance() {
        return this.instance;
    }

    @Override
    public int getW() {
        return this.w;
    }

    @Override
    public int getH() {
        return this.h;
    }

    @Override
    public int startX() {
        return startX;
    }

    @Override
    public void setStartX(int x) {
        startX = x;
    }

    @Override
    public int startY() {
        return startY;
    }

    @Override
    public void setStartY(int y) {
        startY = y;
    }

    @Override
    public int endX() {
        return startX + getW();
    }

    @Override
    public int endY() {
        return startY + getH();
    }

    @Override
    public String getStatement(int x, int y) {
        for (int k = 0; k != this.statementCoordinates.length; ++k) {
            var coord = this.statementCoordinates[k];
            if (coord[0] == x && coord[1] == y) {
                int j = 0;
                for (var key : instance.statements.keySet()) {
                    if (j == k) {
                        return instance.statements.get(key);
                    }
                    ++j;
                }
            }
        }
        return "";
    }

    @Override
    public ArrayList<Integer> getEntityIds() {
        return this.entityIds;
    }

    @Override
    public ArrayList<Point> getStatementCells() {
        ArrayList<Point> coords = new ArrayList<>();
        
        for (int i = 0; i < statementCoordinates.length; i++) {
            coords.add(new Point(statementCoordinates[i][0], statementCoordinates[i][1]));
        }

        return coords;
    }

    @Override
    public ArrayList<Point> getCells() {
        return this.cells;
    }

//    @Override
    public void setCells() {
        cells = new ArrayList<>();

        for (int i = startX; i <= endX(); i++) {
            for (int j = startY; j <= endY(); j++) {
                if (entityCovers(i, j)) {
                    cells.add(new Point(i, j));
                }
            }
        }
    }

    private boolean entityCovers(int x, int y) {
        for (int i = 0; i < entities.length; i++) {
            if (entities[i][y][0] == 1) {
                if (entities[i][y][1] <= x && entities[i][y][2] >= x) {
                    return true;
                }
            }
        }

        return false;
    }

    /// The below transformation functions work only when startX and startY are 0.
    @Override
    public void mirror(boolean mirrorX, boolean mirrorY) {
        int[][][] newEntities = new int[entities.length][h + 1][3];

        for (int e = 0; e < entities.length; e++) {
            for (int y = 0; y <= h; y++) {
                int targetY = mirrorY ? (h - y) : y;

                int active = entities[e][y][0];

                if (active == 1) {
                    int startX = entities[e][y][1];
                    int endX   = entities[e][y][2];

                    if (mirrorX) {
                        int mirroredStart = w - endX;
                        int mirroredEnd   = w - startX;

                        newEntities[e][targetY][0] = 1;
                        newEntities[e][targetY][1] = mirroredStart;
                        newEntities[e][targetY][2] = mirroredEnd;
                    } else {
                        newEntities[e][targetY][0] = 1;
                        newEntities[e][targetY][1] = startX;
                        newEntities[e][targetY][2] = endX;
                    }
                } else {
                    newEntities[e][targetY][0] = 0;
                }
            }
        }

        this.entities = newEntities;

        if (statementCoordinates != null) {
            for (int i = 0; i < statementCoordinates.length; i++) {
                int x = statementCoordinates[i][0];
                int y = statementCoordinates[i][1];

                if (mirrorX) x = w - x;
                if (mirrorY) y = h - y;

                statementCoordinates[i][0] = x;
                statementCoordinates[i][1] = y;
            }
        }

        // Recompute cells after transformation
        setCells();
    }

    // Clockwise 90 degree rotation
    @Override
    public void rotate90() {
        int newW = this.h;
        int newH = this.w;

        int[][][] newEntities = new int[entities.length][newH + 1][3];

        for (int e = 0; e < entities.length; e++) {
            // Fill the new structure by scanning all old cells
            for (int oldY = 0; oldY <= h; oldY++) {
                if (entities[e][oldY][0] == 1) {

                    int startX = entities[e][oldY][1];
                    int endX   = entities[e][oldY][2];

                    for (int oldX = startX; oldX <= endX; oldX++) {

                        int newX = oldY;
                        int newY = w - oldX;

                        // Mark new cell as active in segment form
                        if (newEntities[e][newY][0] == 0) {
                            newEntities[e][newY][0] = 1;
                            newEntities[e][newY][1] = newX;
                            newEntities[e][newY][2] = newX;
                        } else {
                            // extend segment
                            newEntities[e][newY][1] = Math.min(newEntities[e][newY][1], newX);
                            newEntities[e][newY][2] = Math.max(newEntities[e][newY][2], newX);
                        }
                    }
                }
            }
        }

        // Replace object state
        this.entities = newEntities;
        this.w = newW;
        this.h = newH;

        if (statementCoordinates != null) {
            for (int i = 0; i < statementCoordinates.length; i++) {
                int oldX = statementCoordinates[i][0];
                int oldY = statementCoordinates[i][1];

                int newX = oldY;
                int newY = (newH) - oldX;

                statementCoordinates[i][0] = newX;
                statementCoordinates[i][1] = newY;
            }
        }

        // recompute cells
        setCells();
    }

    @Override
    public PolygonSolution copy() {
        // deep copy 3D entities array
        int[][][] newEntities = new int[entities.length][][];
        for (int i = 0; i < entities.length; i++) {
            newEntities[i] = new int[entities[i].length][3];
            for (int j = 0; j < entities[i].length; j++) {
                System.arraycopy(entities[i][j], 0, newEntities[i][j], 0, 3);
            }
        }

        // deep copy statementCoordinates
        int[][] newStatementCoords = null;
        if (statementCoordinates != null) {
            newStatementCoords = new int[statementCoordinates.length][statementCoordinates[0].length];
            for (int i = 0; i < statementCoordinates.length; i++) {
                System.arraycopy(statementCoordinates[i], 0, newStatementCoords[i], 0, statementCoordinates[i].length);
            }
        }

        // list copy
        ArrayList<Integer> newEntityIds = new ArrayList<>(this.entityIds);

        return new PolygonSolution(
                this.instance,
                this.w,
                this.h,
                newEntityIds,
                newEntities,
                newStatementCoords
        );
    }
}

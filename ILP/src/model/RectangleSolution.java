package model;

import java.awt.Point;
import java.util.ArrayList;

public class RectangleSolution implements Solution {
    public StatementEntityInstance instance;
    public int w;
    public int h;
    ArrayList<Integer> entityIds;
    public int[][] entityCoordinates;
    public int[][] statementCoordinates;
    public ArrayList<Point> cells;

    public RectangleSolution(StatementEntityInstance inst, int w, int h, ArrayList<Integer> eIds, int[][] eCoords,
            int[][] sCoords) {
        this.instance = inst;
        this.w = w;
        this.h = h;
        this.entityIds = eIds;
        this.entityCoordinates = eCoords;
        this.statementCoordinates = sCoords;
        this.cells = setCells();
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
    public ArrayList<Point> getCells() {
        return this.cells;
    }

    @Override
    public ArrayList<Point> setCells() {
        ArrayList<Point> coveredCells = new ArrayList<>();

        for (int i = 0; i <= w; i++) {
            for (int j = 0; j <= h; j++) {
                if (entityCovers(i, j)) {
                    coveredCells.add(new Point(i, j));
                }
            }
        }

        return coveredCells;
    }

    private boolean entityCovers(int x, int y) {
        for (int i = 0; i < entityCoordinates.length; i++) {
            if (entityCoordinates[i][0] <= x && entityCoordinates[i][2] >= x) {
                if (entityCoordinates[i][1] <= y && entityCoordinates[i][3] >= y) {
                    return true;
                }
            }
        }

        return false;
    }

    public void mirror(boolean mirrorX, boolean mirrorY) {
        for (int i = 0; i < entityCoordinates.length; i++) {
            int x1 = entityCoordinates[i][0];
            int y1 = entityCoordinates[i][1];
            int x2 = entityCoordinates[i][2];
            int y2 = entityCoordinates[i][3];

            if (mirrorX) {
                int nx1 = w - x2;
                int nx2 = w - x1;
                x1 = nx1;
                x2 = nx2;
            }

            if (mirrorY) {
                int ny1 = h - y2;
                int ny2 = h - y1;
                y1 = ny1;
                y2 = ny2;
            }

            entityCoordinates[i][0] = x1;
            entityCoordinates[i][1] = y1;
            entityCoordinates[i][2] = x2;
            entityCoordinates[i][3] = y2;
        }

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

        // regenerate cells
        this.cells = setCells();
    }

    /// Clockwise
    public void rotate90() {
        int newW = this.h;
        int newH = this.w;

        for (int i = 0; i < entityCoordinates.length; i++) {
            int x1 = entityCoordinates[i][0];
            int y1 = entityCoordinates[i][1];
            int x2 = entityCoordinates[i][2];
            int y2 = entityCoordinates[i][3];

            // transform all four corners
            int[] xs = new int[4];
            int[] ys = new int[4];

            int[][] pts = {
                    {x1, y1},
                    {x2, y1},
                    {x2, y2},
                    {x1, y2}
            };

            for (int p = 0; p < 4; p++) {
                int x = pts[p][0];
                int y = pts[p][1];

                int rx = y;       // new x
                int ry = w - x;   // new y

                xs[p] = rx;
                ys[p] = ry;
            }

            // build new rectangle bounding box
            int nx1 = Integer.MAX_VALUE;
            int ny1 = Integer.MAX_VALUE;
            int nx2 = Integer.MIN_VALUE;
            int ny2 = Integer.MIN_VALUE;

            for (int p = 0; p < 4; p++) {
                nx1 = Math.min(nx1, xs[p]);
                ny1 = Math.min(ny1, ys[p]);
                nx2 = Math.max(nx2, xs[p]);
                ny2 = Math.max(ny2, ys[p]);
            }

            entityCoordinates[i][0] = nx1;
            entityCoordinates[i][1] = ny1;
            entityCoordinates[i][2] = nx2;
            entityCoordinates[i][3] = ny2;
        }

        // update dimensions
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
        this.cells = setCells();
    }

    public RectangleSolution copy() {
        // deep-copy entityCoordinates
        int[][] newEntityCoords = new int[entityCoordinates.length][4];
        for (int i = 0; i < entityCoordinates.length; i++) {
            System.arraycopy(entityCoordinates[i], 0, newEntityCoords[i], 0, 4);
        }

        // deep-copy statementCoordinates
        int[][] newStatementCoords = null;
        if (statementCoordinates != null) {
            newStatementCoords = new int[statementCoordinates.length][statementCoordinates[0].length];
            for (int i = 0; i < statementCoordinates.length; i++) {
                System.arraycopy(statementCoordinates[i], 0, newStatementCoords[i], 0, statementCoordinates[i].length);
            }
        }

        // copy entityIds list (shallow copy is fine since elements are immutable Integers)
        ArrayList<Integer> newEntityIds = new ArrayList<>(this.entityIds);

        return new RectangleSolution(
                this.instance,   // referencing same instance is intended
                this.w,
                this.h,
                newEntityIds,
                newEntityCoords,
                newStatementCoords
        );
    }
}

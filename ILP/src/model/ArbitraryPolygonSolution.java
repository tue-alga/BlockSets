package model;

import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;

public class ArbitraryPolygonSolution implements Solution {
    public StatementEntityInstance instance;
    public int w;
    public int h;
    ArrayList<Integer> entityIds;
//    public ArrayList<ArrayList<Point>> entityBoundaries;
    public ArrayList<ArrayList<Point>> entityCells;
    public Point[] statementCoordinates;
    public ArrayList<Point> cells;

    public ArbitraryPolygonSolution(StatementEntityInstance inst, ArrayList<Integer> eIds, //ArrayList<ArrayList<Point>> entityBoundaries,
                                    ArrayList<ArrayList<Point>> entityCells, Point[] sCoords) {
        this.instance = inst;
        this.entityIds = eIds;
//        this.entityBoundaries = entityBoundaries;
        this.entityCells = entityCells;
        this.statementCoordinates = sCoords;
        this.cells = setCells();

        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (Point p : cells) {
            if (p.x > maxX) maxX = p.x;
            if (p.y > maxY) maxY = p.y;
        }

        this.w = maxX;
        this.h = maxY;
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
            if (coord.x == x && coord.y == y) {
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
        return new ArrayList<>(Arrays.asList(statementCoordinates));
    }

    @Override
    public ArrayList<Point> getCells() {
        return this.cells;
    }

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
        for (var cells : entityCells) {
            for (var cell : cells) {
                if (cell.getX() == x && cell.getY() == y) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void mirror(boolean mirrorX, boolean mirrorY) {
        // mirror entity cells and entity boundaries

//        for (var boundary : entityBoundaries) {
//            for (var pt : boundary) {
//                if (mirrorX) {
//                    pt.x = w - pt.x;
//                }
//                if (mirrorY) {
//                    pt.y = h - pt.y;
//                }
//            }
//        }

        for (var cells : entityCells) {
            for (var pt : cells) {
                if (mirrorX) {
                    pt.x = w - pt.x;
                }
                if (mirrorY) {
                    pt.y = h - pt.y;
                }
            }
        }

        this.cells = setCells();

        for (Point statementCoordinate : statementCoordinates) {
            int x = statementCoordinate.x;
            int y = statementCoordinate.y;

            if (mirrorX) x = w - x;
            if (mirrorY) y = h - y;

            statementCoordinate.x = x;
            statementCoordinate.y = y;
        }

        // Recompute cells after transformation
        this.cells = setCells();
    }

    // Clockwise 90 degree rotation
    @Override
    public void rotate90() {
        int oldW = this.w;
        int oldH = this.h;

        // Rotate boundaries
//        for (var boundary : entityBoundaries) {
//            for (var pt : boundary) {
//                int oldX = pt.x;
//                int oldY = pt.y;
//
//                pt.x = oldY;
//                pt.y = oldW - oldX;
//            }
//        }

        // Rotate cells
        for (var cells : entityCells) {
            for (var pt : cells) {
                int oldX = pt.x;
                int oldY = pt.y;

                pt.x = oldY;
                pt.y = oldW - oldX;
            }
        }

        // Rotate statement coordinates
        for (Point statementCoordinate : statementCoordinates) {
            int oldX = statementCoordinate.x;
            int oldY = statementCoordinate.y;

            statementCoordinate.x = oldY;
            statementCoordinate.y = oldW - oldX;
        }

        // Swap width and height
        this.w = oldH;
        this.h = oldW;

        // Recompute cells after transformation
        this.cells = setCells();
    }


    @Override
    public ArbitraryPolygonSolution copy() {
        ArrayList<Integer> newEntityIds = new ArrayList<>(this.entityIds);

        // Deep copy entityBoundaries
        ArrayList<ArrayList<Point>> newEntityBoundaries = null;
//        if (this.entityBoundaries != null) {
//            newEntityBoundaries = new ArrayList<>(this.entityBoundaries.size());
//            for (ArrayList<Point> boundary : this.entityBoundaries) {
//                ArrayList<Point> newBoundary = new ArrayList<>(boundary.size());
//                for (Point p : boundary) {
//                    newBoundary.add(new Point(p.x, p.y));
//                }
//                newEntityBoundaries.add(newBoundary);
//            }
//        }

        // Deep copy entityCells
        ArrayList<ArrayList<Point>> newEntityCells = null;
        if (this.entityCells != null) {
            newEntityCells = new ArrayList<>(this.entityCells.size());
            for (ArrayList<Point> cellList : this.entityCells) {
                ArrayList<Point> newCellList = new ArrayList<>(cellList.size());
                for (Point p : cellList) {
                    newCellList.add(new Point(p.x, p.y));
                }
                newEntityCells.add(newCellList);
            }
        }

        // Deep copy statementCoordinates
        Point[] newStatementCoords = null;
        if (this.statementCoordinates != null) {
            newStatementCoords = new Point[this.statementCoordinates.length];
            for (int i = 0; i < statementCoordinates.length; i++) {
                Point p = statementCoordinates[i];
                newStatementCoords[i] = new Point(p.x, p.y);
            }
        }

        // Deep copy cells
        ArrayList<Point> newCells = null;
        if (this.cells != null) {
            newCells = new ArrayList<>(this.cells.size());
            for (Point p : this.cells) {
                newCells.add(new Point(p.x, p.y));
            }
        }

        ArbitraryPolygonSolution copy = new ArbitraryPolygonSolution(
                this.instance,
                newEntityIds,
//                newEntityBoundaries,
                newEntityCells,
                newStatementCoords
        );

        copy.cells = newCells;

        return copy;
    }

}

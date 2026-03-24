package model;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

public interface Solution {
    public int startX = 0;
    public int startY = 0;

    public int startX();
    public int startY();
    public void setStartX(int x);
    public void setStartY(int y);

    public default int endX() {
        return startX + getW();
    }


    public default int endY() {
        return startY + getH();
    }

    StatementEntityInstance getInstance();

    int getW();

    int getH();

    ArrayList<Integer> getEntityIds();
    ArrayList<Point> getStatementCells();

//    ArrayList<Point> setCells();

    ArrayList<Point> getCells();

    String getStatement(int x, int y);

    public void rotate90();

    public void mirror(boolean mirrorX, boolean mirrorY);

    public Solution copy();

    public default Solution mirrored(boolean mirrorX, boolean mirrorY) {
        var c = copy();
        c.mirror(mirrorX, mirrorY);
        return c;
    }

    public default Solution rotated90() {
        var c = copy();
        c.rotate90();
        return c;
    }

    public default boolean occupySameCells(Solution other) {
        Set<Point> aSet = new HashSet<>(getCells());
        Set<Point> bSet = new HashSet<>(other.getCells());
        return aSet.equals(bSet);
    }

    public default boolean hasCell(int x, int y) {
        var cells = getCells();
        for (var cell : cells) {
            if (cell.x == x && cell.y == y) {
                return true;
            }
        }
        return false;
    }

    public default boolean hasStatement(int x, int y) {
        String s = getStatement(x, y);
        return s != null && !s.isEmpty();
    }

    public default boolean isUsed(int x, int y) {
        return hasStatement(x, y) || hasCell(x, y);
    }

    public default List<Point> usedCells() {
        ArrayList<Point> used = new ArrayList<>();

        for (int x = startX; x <= endX(); x++) {
            for (int y = startY; y <= endY(); y++) {
                if (isUsed(x, y)) used.add(new Point(x, y));
            }
        }

        return used;
    }

    /// Return the blank cells within the orthoconvex hull of used cells
    /// Blank = cell in the bounding box that has no set nor an element
    /// Note that in most cases we are interested in the blank orthoconvex hull cells of the PositionedSolution.
    public default List<Point> blankOrthoconvexHullCells() {
        Set<Point> gaps = new HashSet<>();
        for (int x = startX; x <= endX(); ++x) {
            int minY = endY();
            int maxY = startY;
            for (int y = startY; y <= endY(); ++y) {
                if (isUsed(x, y)) {
                    if (y < minY) {
                        minY = y;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }

            for (int y = minY; y <= maxY; ++y) {
                if (!isUsed(x, y)) {
                    gaps.add(new Point(x, y));
                }
            }
        }
        for (int y = startY; y <= endY(); ++y) {
            int minX = endX();
            int maxX = startX;
            for (int x = startX; x <= endX(); ++x) {
                if (isUsed(x, y)) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                }
            }

            for (int x = minX; x <= maxX; ++x) {
                if (!isUsed(x, y)) {
                    gaps.add(new Point(x, y));
                }
            }
        }

        return gaps.stream().toList();
    }

    /// Return the vacant cells: cells that have a set but not an element.
    public default List<Point> vacantCells() {
        ArrayList<Point> vacant = new ArrayList<>();
        for (Point cell : getCells()) {
            if (!hasStatement(cell.x, cell.y)) {
                vacant.add(cell);
            }
        }
        return vacant;
    }
}

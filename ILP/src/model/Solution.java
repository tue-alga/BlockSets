package model;

import java.util.ArrayList;
import java.awt.Point;
import java.util.HashSet;
import java.util.Set;

public interface Solution {
    StatementEntityInstance getInstance();

    int getW();

    int getH();

    ArrayList<Integer> getEntityIds();

    ArrayList<Point> setCells();

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

}

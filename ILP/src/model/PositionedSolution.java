package model;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PositionedSolution {
    public final ArrayList<Solution> solutions;
    public final int width;
    public final int height;

    public PositionedSolution(ArrayList<Solution> solutions, int width, int height) {
        this.solutions = solutions;
        this.width = width;
        this.height = height;
    }

    public final static PositionedSolution EMPTY = new PositionedSolution(new ArrayList<Solution>(), 0, 0);

    boolean isUsed(int x, int y) {
        for (var s : solutions) {
            if (s.isUsed(x, y)) return true;
        }
        return false;
    }

    /// Return the blank cells within the orthoconvex hull of used cells
    /// Blank = cell in the bounding box that has no set nor an element
    public List<Point> blankOrthoconvexHullCells() {
        Set<Point> gaps = new HashSet<>();
        for (int x = 0; x < width; ++x) {
            int minY = height;
            int maxY = 0;
            for (int y = 0; y < height; ++y) {
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
        for (int y = 0; y < height; ++y) {
            int minX = width;
            int maxX = 0;
            for (int x = 0; x < width; ++x) {
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
}
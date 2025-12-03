package model;

import java.util.ArrayList;
import java.util.List;

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
}
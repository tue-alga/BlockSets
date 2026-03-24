package io;

import java.util.ArrayList;
import java.util.HashMap;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

import model.PolygonSolution;
import model.PositionedSolution;
import model.RectangleSolution;
import model.Solution;
import model.StatementEntityInstance;
import split.IntersectionGraph;
import split.SplitIntanceFactory;

public class StatsRecorder {
    public String[] parameters; // [dataset name, split type, shape type]

    public int numSets;
    public int numElements;
    public int numNonSingletonSets = 0;

    public int numComponents = 0;
    public int numSetCopies = 0;
    public int numDuplicatedSets = 0;
    public int maxMinComponentDiffSets = 0;
    public int maxMinComponentDiffElements = 0;
    public double totalSplitTime = 0; // in seconds
    public double totalLayoutTime = 0; // in seconds
    public double totalArrangeTime = 0; // in seconds

    public int bboxWidth = 0;
    public int bboxHeight = 0;
    public int totalBBoxSize = 0;
    public int numVacantCells = 0; // cells covered by a set but with no element
    public int numBlankCells = 0; // cells with no set and no element
    public int numBlankOrthoconvexHullCells = 0;
    public double sparsity = 0;
    public double averageNumSetVertices = 0;
    public double averageNumStraightSetSides = 0;
    public double averageSetSquareness = 0;

    public StatsRecorder(StatementEntityInstance inst, String[] params) {
        this.parameters = params;

        this.numSets = inst.numberOfEntities;
        this.numElements = inst.numberOfStatements;

        for (int[] elementList : inst.entityIndToStatements.values()) {
            if (elementList.length > 1)
                numNonSingletonSets++;
        }
    }

    public void updateSetStatsRectangles(RectangleSolution sol) {
        for (int i = 0; i < sol.entityCoordinates.length; i++) {
            int[] coords = sol.entityCoordinates[i];
            int setWidth = coords[2] - coords[0] + 1;
            int setHeight = coords[3] - coords[1] + 1;

            this.averageSetSquareness += (Math.min(setWidth, setHeight) / Math.max(setWidth, setHeight));
            this.averageNumStraightSetSides += 4;
            this.averageNumSetVertices += 4;
        }
    }

    public void updateSetStatsPolygons(PolygonSolution sol) {
        for (int i = 0; i < sol.entities.length; i++) {
            int[][] set = sol.entities[i];

            // Bounding box
            int xStart = Integer.MAX_VALUE, xEnd = -1, yStart = -1, yEnd = -1;
            for (int j = 0; j < set.length; j++) {
                if (yStart == -1 && set[j][0] == 1) {
                    yStart = j;
                }

                if (j == set.length - 1) {
                    yEnd = j;
                    break;
                }

                if (yEnd == -1 && set[j][0] == 1 && set[j + 1][0] == 0) {
                    yEnd = j;
                }
            }

            for (int j = yStart; j <= yEnd; j++) {
                if (xStart > set[j][1]) {
                    xStart = set[j][1];
                }

                if (xEnd < set[j][2]) {
                    xEnd = set[j][2];
                }
            }

            // Squareness
            double setWidth = xEnd - xStart + 1;
            double setHeight = yEnd - yStart + 1;

            this.averageSetSquareness += (Math.min(setWidth, setHeight) / Math.max(setWidth, setHeight));

            // #Vertices
            int numVertices = 4;

            for (int j = yStart; j < yEnd; j++) {
                if (set[j][0] == 0 || set[j][1] == 0) continue;

                if (set[j][1] != set[j + 1][1]) {
                    numVertices += 2;
                }

                if (set[j][2] != set[j + 1][2]) {
                    numVertices += 2;
                }
            }

            this.averageNumSetVertices += numVertices;

            // #straightSides
            int numStraightSides = 0;
            // Top side is straight
            if (set[yStart][1] == xStart && set[yStart][2] == xEnd) {
                numStraightSides++;
            }

            // Bottom side is straight
            if (set[yEnd][1] == xStart && set[yEnd][2] == xEnd) {
                numStraightSides++;
            }

            boolean leftStraight = true;
            boolean rightStraight = true;
            for (int j = yStart; j < yEnd; j++) {
                if (set[j][1] != set[j + 1][1]) {
                    leftStraight = false;
                }

                if (set[j][2] != set[j + 1][2]) {
                    rightStraight = false;
                }
            }

            if (leftStraight)
                numStraightSides++;
            if (rightStraight)
                numStraightSides++;

            this.averageNumStraightSetSides += numStraightSides;
        }
    }

    public void updateShapeStatsSingleComponent(Solution sol) {
        if (sol instanceof RectangleSolution) {
            updateSetStatsRectangles((RectangleSolution) sol);
        }
        if (sol instanceof PolygonSolution) {
            updateSetStatsPolygons((PolygonSolution) sol);
        }
        this.numVacantCells += sol.vacantCells().size();
    }

    public void updateShapeStatsFinalLayout(PositionedSolution sol) {
        this.bboxWidth = sol.width + 1;
        this.bboxHeight = sol.height + 1;
        this.totalBBoxSize = (sol.width + 1) * (sol.height + 1);

        // Collect the number of all cells covered by any component in the layout
        int usedCells = 0;
        for (Solution s : sol.solutions) {
            usedCells += s.usedCells().size();
        }

        this.numBlankOrthoconvexHullCells = sol.blankOrthoconvexHullCells().size();

        // Any cell in the bounding box that is not used by any component counts as blank
        this.numBlankCells = this.totalBBoxSize - usedCells;

        // Individual set stats only store totals so far, divide to get the average
        this.averageNumSetVertices /= (this.numSets + this.numSetCopies);
        this.averageNumStraightSetSides /= (this.numSets + this.numSetCopies);
        this.averageSetSquareness /= (this.numSets + this.numSetCopies);

        // Get the fraction of vacant cells
        this.sparsity = (double) this.numVacantCells / this.totalBBoxSize;
    }

    public void updateSplitComponentStats(ArrayList<StatementEntityInstance> instances) {
        ArrayList<StatementEntityInstance> disjointInstances = getDisjointSplitInstances(instances);

        this.numComponents = disjointInstances.size();

        // Store all copies of each entity
        HashMap<String, Integer> numSetCopies = new HashMap<>();

        for (StatementEntityInstance inst : disjointInstances) {
            for (String entity : inst.entities.values()) {
                if (numSetCopies.containsKey(entity)) {
                    numSetCopies.put(entity, numSetCopies.get(entity) + 1);
                } else {
                    numSetCopies.put(entity, 1);
                }
            }
        }

        for (String entity : numSetCopies.keySet()) {
            if (numSetCopies.get(entity) > 1) {
                this.numDuplicatedSets++;
                this.numSetCopies += numSetCopies.get(entity) - 1; // do not count the original
            }
        }

        for (int i = 0; i < disjointInstances.size(); i++) {
            for (int j = i; j < disjointInstances.size(); j++) {
                int setDiff = Math
                        .abs(disjointInstances.get(i).numberOfEntities - disjointInstances.get(j).numberOfEntities);
                int elementDiff = Math
                        .abs(disjointInstances.get(i).numberOfStatements - disjointInstances.get(j).numberOfStatements);

                if (this.maxMinComponentDiffSets < setDiff) {
                    this.maxMinComponentDiffSets = setDiff;
                }

                if (this.maxMinComponentDiffElements < elementDiff) {
                    this.maxMinComponentDiffElements = elementDiff;
                }
            }
        }
    }

    public void updateRunningTimeStats() {

    }

    // Get actual connected components (merge could have combined two or more disjoint components into one)
    private ArrayList<StatementEntityInstance> getDisjointSplitInstances(ArrayList<StatementEntityInstance> instances) {
        ArrayList<StatementEntityInstance> disjointInstances = new ArrayList<>();

        for (StatementEntityInstance inst : instances) {
            IntersectionGraph instGraph = new IntersectionGraph(inst);
            ArrayList<StatementEntityInstance> subInstances = new SplitIntanceFactory(inst, instGraph)
                    .createInstances();
            disjointInstances.addAll(subInstances);
        }

        return disjointInstances;
    }

    public void print() {
        System.out.println("----------------SPLIT STATS----------------");
        System.out.println("Components: " + this.numComponents);
        System.out.println("Duplicated sets: " + this.numDuplicatedSets);
        System.out.println("Set copies: " + this.numSetCopies);
        System.out.println("Max set size difference: " + this.maxMinComponentDiffSets);
        System.out.println("Max element size difference: " + this.maxMinComponentDiffElements);
        System.out.println("Vacant cells: " + this.numVacantCells);
        System.out.println("Sparcity (%): " + this.sparsity * 100 + "%");
        System.out.println("Total split time: " + this.totalSplitTime + "s");
        System.out.println("-------------------------------------------");

        System.out.println();

        System.out.println("----------------SHAPE STATS----------------");
        System.out.println("Bounding box (cells): " + this.totalBBoxSize);
        System.out.println("Blank cells: " + this.numBlankCells);
        System.out.println("Average set squareness (normalized range: 0 - 1): " + this.averageSetSquareness);
        System.out.println("Average set vertices: " + this.averageNumSetVertices);
        System.out.println("Average set straight sides: " + this.averageNumStraightSetSides);
        System.out.println("-------------------------------------------");
    }

    public void appendToCsv(File file) {
        String dataset = this.parameters[0];
        String split = this.parameters[1];
        String setShape = this.parameters[2];

        boolean writeHeader = !file.exists() || file.length() == 0;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {

            if (writeHeader) {
                writer.write(
                        "Dataset,Split,Shape," +
                                "Components,Duplicated sets,Set copies," +
                                "Max set size difference,Max element size difference," +
                                "Vacant cells,Sparsity (%),Total split time (s),Total layout time (s),Total arrange time (s)," +
                                "Bounding box width,Bounding box height," +
                                "Bounding box (cells),Blank cells,Blank orthoconvex hull cells," +
                                "Average set squareness,Average set vertices,Average set straight sides");
                writer.newLine();
            }

            Locale.setDefault(Locale.US);
            writer.write(
                    escapeCsv(dataset) + "," +
                            escapeCsv(split) + "," +
                            escapeCsv(setShape) + "," +
                            this.numComponents + "," +
                            this.numDuplicatedSets + "," +
                            this.numSetCopies + "," +
                            this.maxMinComponentDiffSets + "," +
                            this.maxMinComponentDiffElements + "," +
                            this.numVacantCells + "," +
                            String.format("%.2f", this.sparsity * 100) + "," +
                            String.format("%.4f", this.totalSplitTime) + "," +
                            String.format("%.4f", this.totalLayoutTime) + "," +
                            String.format("%.4f", this.totalArrangeTime) + "," +
                            this.bboxWidth + "," +
                            this.bboxHeight + "," +
                            this.totalBBoxSize + "," +
                            this.numBlankCells + "," +
                            this.numBlankOrthoconvexHullCells + "," +
                            String.format("%.4f", this.averageSetSquareness) + "," +
                            String.format("%.4f", this.averageNumSetVertices) + "," +
                            String.format("%.4f", this.averageNumStraightSetSides));

            writer.newLine();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }

        return value;
    }
}

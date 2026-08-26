package ilp.solvers;

import com.gurobi.gurobi.GRBException;
import ilp.solvers.mosaicsets.Grid;
import ilp.solvers.mosaicsets.GridCanvas;
import ilp.solvers.mosaicsets.SetEmbedder;
import ilp.solvers.mosaicsets.VertexColoring;
import io.StatsRecorder;
import model.ArbitraryPolygonSolution;
import model.Solution;
import model.StatementEntityInstance;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.io.FileWriter;
import java.util.*;
import java.util.List;

public class MosaicSetsSolver implements Solver {
    private final double maxMIPgapInitIt;
    private final double maxMIPgapSubseqIt;
    private final String resultPath = "MosaicSets_run_data_";
    private final boolean renderMosaicSetsSvgs;
    int gridSize;
    int padding;
    int fontSize = 10;

    boolean drawGrid = false;
    boolean drawOutline = true;

    boolean minPerimeter;

    /// The number of ILP iterations (for the eccentricity-based compactness measure)
    private final int iterations;

    // Do not sample the project on the k-gon. Instead place them in the center
    // of gravity
    boolean projects_centered = true;

    public MosaicSetsSolver(double maxMIPgapInitIt, double maxMIPgapSubseqIt, boolean renderMosaicSetsSvgs, boolean minPerimeter) {
        this.maxMIPgapSubseqIt = maxMIPgapSubseqIt;
        this.maxMIPgapInitIt = maxMIPgapInitIt;
        this.renderMosaicSetsSvgs = renderMosaicSetsSvgs;
        this.minPerimeter = minPerimeter;
        this.iterations = minPerimeter ? 1 : 5;
    }

    /// Uses the eccentricity-based compactness measure
    @Override
    public Solution solve(StatementEntityInstance inst, double timeLimit, int dimensions) throws Exception, GRBException {
        SetEmbedder.MINIMIZE_BOUNDARIES = minPerimeter;
        gridSize = calculateGridSize(inst);
        Grid grid = new Grid(dimensions, dimensions, gridSize, gridSize, gridSize, Grid.TYPE_SQUARE);

        List<Set<String>> basemap = new ArrayList<>();
        var allStatements = new HashSet<String>();
        basemap.add(allStatements);
        var statementToStatementIndex = new HashMap<String, Integer>();
        for (var statementIndex : inst.statements.keySet()) {
            var statement = inst.statements.get(statementIndex);
            statementToStatementIndex.put(statement, statementIndex);
            allStatements.add(statement);
        }

        List<Set<String>> overlays = new ArrayList<>();
        var overlayIndexToEntityIndex = new ArrayList<Integer>();
        for (var entityIndex : inst.entities.keySet()) {
            var entity = inst.entities.get(entityIndex);
            var statementIndices = inst.entityIndToStatements.get(entityIndex);
            var overlay = new HashSet<String>();
            overlays.add(overlay);
            overlayIndexToEntityIndex.add(entityIndex);
            for (var statementIndex : statementIndices) {
                overlay.add(inst.statements.get(statementIndex));
            }
        }

        SetEmbedder<String> ge = new SetEmbedder<>(grid.graph, basemap, overlays);

        // For the eccentricity-based compactness, each set requires a center.
        // These are initialized around the center.
        List<Point2D.Double> centers = new ArrayList<>();
        // Create points around center of gravity of the grid with specified radius
        if (projects_centered) {
            centers = grid.getPointsAroundCenter(basemap.size(), 1);
            for (int i = 0; i < overlays.size(); i++)
                centers.add(grid.getCenter());
        } else {
            centers = grid.getPointsAroundCenter(
                    basemap.size() + overlays.size(), 1);
        }

        long startTime = System.currentTimeMillis(), endTimeFirst = 0,
                endTimeSecond = 0;
        Map<Point2D.Double, String> solution = ge.optimize(timeLimit, maxMIPgapInitIt, centers, resultPath);
        if (solution == null) {
            return null;
        }
        endTimeFirst = System.currentTimeMillis();
        List<Point2D.Double> usedCenters = new ArrayList<>();
        for (int i = 1; i < iterations; i++) {
            usedCenters = getCenters(solution, ge.sets);

            // Use the second optimize with mean center positions
            var newSolution = ge.optimize(timeLimit, maxMIPgapSubseqIt, usedCenters, true, resultPath);
            if (newSolution != null) {
                solution = newSolution;
            } else {
                break;
            }
            endTimeSecond = System.currentTimeMillis();
        }

        var statementCoordinates = new Point[inst.numberOfStatements];
        HashMap<String, Point> statementToPoint = new HashMap<>();
        for (var entry : solution.entrySet()) {
            var str = entry.getValue();
            var pt = entry.getKey();
            statementToPoint.put(str, new Point((int) Math.round((pt.x - gridSize) / gridSize), (int) Math.round((pt.y - gridSize) / gridSize)));
        }

        // The code assumes that statementCoordinates are in the same order as in inst.statements.
        var statementIndexToStatementCoordinatesIndex = new HashMap<Integer, Integer>();
        int scI = 0;
        for (var entry : inst.statements.entrySet()) {
            statementCoordinates[scI] = statementToPoint.get(entry.getValue());
            statementIndexToStatementCoordinatesIndex.put(entry.getKey(), scI);
            ++scI;
        }

        var entityIds = new ArrayList<Integer>();
        var entityCells = new ArrayList<ArrayList<Point>>();
        for (int k = ge.nInst; k < ge.sets.size(); ++k) {
            int overlayIndex = k - ge.nInst;
            int entityIndex = overlayIndexToEntityIndex.get(overlayIndex);
            entityIds.add(entityIndex);
            Set<Point> cells = new HashSet<>();

            for (var v : ge.setsToSelectedArcs.get(k)) {
                cells.add(new Point((int) Math.round((v.x1 - gridSize) / gridSize), (int) Math.round((v.y1 - gridSize) / gridSize)));
                cells.add(new Point((int) Math.round((v.x2 - gridSize) / gridSize), (int) Math.round((v.y2 - gridSize) / gridSize)));
            }
            // If the entity is a singleton, then it will have no arcs.
            // Check where the single statement was placed.
            if (inst.entityIndToStatements.get(entityIndex).length == 1) {
                var statement = inst.entityIndToStatements.get(entityIndex)[0];
                cells.add(statementCoordinates[statementIndexToStatementCoordinatesIndex.get(statement)]);
            }
            entityCells.add(new ArrayList<>(cells));
        }

        if (renderMosaicSetsSvgs) {
            GridCanvas<String> gc = new GridCanvas<>(new Grid(dimensions + 2, dimensions + 2, gridSize, 0, 0, Grid.TYPE_SQUARE));

            int borderSize = (int) Math.ceil(0.05 * gridSize);
            Color borderColor = Color.white;
//            int maxFontSize = 20;
            Color fontColor = Color.decode("#353535");

            var basemapColor = Arrays.asList("#eeeeee");
            var overlayColor = Arrays.asList(
                    "#4E79A7", "#E15759", "#59A14F", "#D37295", "#F28E2B",
                    "#9D7660", "#499894", "#79706E", "#B07AA1", "#B6992D",
                    "#d9eaf5", "#f2d5b9", "#d1eccb", "#f9ebc0", "#d4cee1",
                    "#ffd7d6", "#BAB0AC", "#FABFD2", "#D4A6C8", "#D7B5A6"
            );

            // Running the vertex coloring takes too long so skip that for now
//            VertexColoring<String> vc = new VertexColoring<>(overlays, solution,
//                    grid.graph, Grid.TYPE_SQUARE);
//            vc.colorGraph();
//            vc.numOfColors = 25;
//            vc.solveColoring(0);

            // define colors for sets
            Color[] instituteFillColors = new Color[basemap.size()];
            Color[] projectArcColors = new Color[overlays.size()];
            int[] arcOrder = new int[overlays.size()];
            for (int i = 0; i < basemap.size(); i++) {
                Color c = Color.decode(basemapColor.get(i));
                instituteFillColors[i] = c;
            }
            for (int i = 0; i < overlays.size(); i++) {
                projectArcColors[i] = Color.decode(overlayColor.get(i));
            }
            for (int i = 0; i < overlays.size(); i++) {
//                arcOrder[i] = vc.getColor(i);
                arcOrder[i] = i;
            }
            
            gc.addSolution(basemap, solution, ge.setsToSelectedArcs, centers,
                    usedCenters, instituteFillColors, projectArcColors, drawGrid,
                    drawOutline, false, arcOrder, borderColor, borderSize, true,
                    fontSize, fontColor, false, false);
            gc.export(resultPath + "gridset.svg");
        }

        return new ArbitraryPolygonSolution(inst, entityIds, entityCells, statementCoordinates);
    }

    /**
     * Computes the geometric center of each individual set.
     *
     * @param  solution Annotated point map with the corresponding sets
     * @param  sets     List of sets
     * @return          List of centers of gravity of each set
     */
    public static List<Point2D.Double> getCenters(
            Map<Point2D.Double, String> solution, List<Set<String>> sets) {
        List<Point2D.Double> centers = new ArrayList<>();
        for (Set<String> set : sets) {
            double sumX = 0, sumY = 0;
            for (Map.Entry<Point2D.Double, String> entry : solution.entrySet()) {
                if (set.contains(entry.getValue())) {
                    sumX += entry.getKey().getX();
                    sumY += entry.getKey().getY();
                }
            }
            centers.add(new Point2D.Double(sumX / set.size(), sumY / set.size()));
        }
        return centers;
    }

    /**
     * Computes the smallest grid cell size that fits every element.
     *
     * @param inst Instance containing the text associated with every element
     * @return Smallest possible size of a grid cell side
     */
    private int calculateGridSize(StatementEntityInstance inst) {
        BufferedImage image =
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = image.createGraphics();

        Font font = new Font("Cambria", Font.PLAIN, fontSize);
        g.setFont(font);

        FontMetrics fm = g.getFontMetrics();

        int low = fm.getHeight();
        int high = low;

        // Find an upper bound.
        while (!allLabelsFit(inst, high, fm)) {
            high *= 2;
        }

        // Find the smallest fitting grid size.
        while (low < high) {
            int mid = low + (high - low) / 2;

            if (allLabelsFit(inst, mid, fm)) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }

        g.dispose();

        // Store the padding corresponding to the FINAL grid size.
        padding = (int) Math.ceil(0.08 * low);

        return low;
    }


    private boolean allLabelsFit(
            StatementEntityInstance inst,
            int gridSize,
            FontMetrics fm) {

        for (String text : inst.statements.values()) {
            if (!fitsInCell(text, gridSize, fm)) {
                return false;
            }
        }

        return true;
    }


    private boolean fitsInCell(
            String text,
            int gridSize,
            FontMetrics fm) {

        // Padding must be calculated for THIS candidate grid size.
        int candidatePadding =
                (int) Math.ceil(0.08 * gridSize);

        // gridSize - padding on the left - padding on the right.
        int availableSize =
                gridSize - 2 * candidatePadding;

        if (availableSize <= 0) {
            return false;
        }

        String[] pieces = text.split("(?<=[\\s-]+)");
        List<String> finalPieces = new ArrayList<>();

        String curStr = "";

        for (String t : pieces) {
            if (availableSize < fm.stringWidth(curStr + t.trim())) {

                if (!curStr.isEmpty()) {
                    finalPieces.add(curStr);
                }

                curStr = t;

            } else {
                curStr = curStr + t;
            }
        }

        if (!curStr.isEmpty()) {
            finalPieces.add(curStr);
        }

        // Horizontal fit.
        for (String line : finalPieces) {
            if (fm.stringWidth(line.trim()) > availableSize) {
                return false;
            }
        }

        // Vertical fit.
        int textHeight =
                finalPieces.size() * fm.getAscent();

        return textHeight <= availableSize;
    }
}

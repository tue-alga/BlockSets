package ilp.solvers;

import com.gurobi.gurobi.GRBException;
import ilp.solvers.mosaicsets.Grid;
import ilp.solvers.mosaicsets.SetEmbedder;
import io.StatsRecorder;
import model.ArbitraryPolygonSolution;
import model.Solution;
import model.StatementEntityInstance;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.io.FileWriter;
import java.util.*;
import java.util.List;

public class MosaicSetsSolver implements Solver {
    private final int dimensions;
    private final double maxMIPgapInitIt;
    private final double maxMIPgapSubseqIt;
    private final String resultPath = "MosaicSets_run_data";

    /// The number of ILP iterations (for the eccentricity-based compactness measure)
    private final int iterations = 5;

    // Do not sample the project on the k-gon. Instead place them in the center
    // of gravity
    boolean projects_centered = true;

    public MosaicSetsSolver(int dimensions, double maxMIPgapInitIt, double maxMIPgapSubseqIt) {
        this.dimensions = dimensions;
        this.maxMIPgapSubseqIt = maxMIPgapSubseqIt;
        this.maxMIPgapInitIt = maxMIPgapInitIt;
    }

    /// Uses the eccentricity-based compactness measure
    @Override
    public Solution solve(StatementEntityInstance inst, double timeLimit) throws Exception, GRBException {
        // todo: check if grid dimensions are consistent
        Grid grid = new Grid(dimensions, dimensions, 1, 0.0, 0.0, Grid.TYPE_SQUARE);

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
        Map<Point2D.Double, String> solution = ge.optimize(maxMIPgapInitIt, centers, resultPath);
        endTimeFirst = System.currentTimeMillis();
        List<Point2D.Double> usedCenters = new ArrayList<>();
        for (int i = 1; i < iterations; i++) {
            usedCenters = getCenters(solution, ge.sets);

            // Use the second optimize with mean center positions
            solution = ge.optimize(maxMIPgapSubseqIt, usedCenters, true, resultPath);
            endTimeSecond = System.currentTimeMillis();
        }

        int maxX = 0;
        int maxY = 0;
        for (var k : solution.keySet()) {
            System.out.println(k.toString());
            if (k.getX() > maxX) maxX = (int) k.getX();
            if (k.getY() > maxY) maxY = (int) k.getY();
        }

        var statementCoordinates = new Point[inst.numberOfStatements];
        for (var entry : solution.entrySet()) {
            int i = statementToStatementIndex.get(entry.getValue());
            var p2d = entry.getKey();
            statementCoordinates[i] = new Point((int) Math.round(p2d.x), (int) Math.round(p2d.y));
        }

        var entityIds = new ArrayList<Integer>();
        var entityCells = new ArrayList<ArrayList<Point>>();
        for (int k = ge.nInst; k < ge.sets.size(); ++k) {
            int overlayIndex = k - ge.nInst;
            int entityIndex = overlayIndexToEntityIndex.get(overlayIndex);
            entityIds.add(entityIndex);
            Set<Point> cells = new HashSet<>();

            for (var v : ge.setsToSelectedArcs.get(k)) {
                cells.add(new Point((int) Math.round(v.x1), (int) Math.round(v.y1)));
                cells.add(new Point((int) Math.round(v.x2), (int) Math.round(v.y2)));
            }
            // If the entity is a singleton, then it will have no arcs.
            // Check where the single statement was placed.
            if (inst.entityIndToStatements.get(entityIndex).length == 1) {
                var statement = inst.entityIndToStatements.get(entityIndex)[0];
                cells.add(statementCoordinates[statement]);
            }
            entityCells.add(new ArrayList<>(cells));
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
}

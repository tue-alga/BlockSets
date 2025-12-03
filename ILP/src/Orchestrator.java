import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

import com.gurobi.gurobi.GRBException;

import ilp.PolygonType;
import ilp.constraints.*;
import ilp.objective.CompactSquareTopLeft;
import ilp.objective.ObjectiveModule;
import ilp.objective.PolygonAreaDimensionsComplexity;
import ilp.solvers.SolutionPositioner;
import ilp.solvers.StatementEntitySolver;
import io.SolutionWriter;
import io.StatementEntityReader;
import model.PositionedSolution;
import model.Solution;
import model.StatementEntityInstance;
import split.GreedySplit;

public class Orchestrator {

    private final int splitK; // Maximum number of nodes to be deleted (usually 5)
    private final double splitRatio; // Coefficient that determines how wide is the range of acceptable components'
                                     // sizes produced from the split
    private final int maxDimensions = 7;
    private final double componentLayoutTimeLimit;
    private final double componentArrangementTimeLimit;

    public final List<Solution> solutions = new ArrayList<>();
    public final Set<Integer> deletedNodes = new HashSet<>();
    public final Map<Integer, int[]> deletedPositions = new HashMap<>(); // fill later if needed

    public Orchestrator(int splitK, double splitRatio, double componentLayoutTimeLimit, double componentArrangementTimeLimit) {
        this.splitK = splitK;
        this.splitRatio = splitRatio;
        this.componentLayoutTimeLimit = componentLayoutTimeLimit;
        this.componentArrangementTimeLimit = componentArrangementTimeLimit;
    }

    public List<Solution> solveWithSplits(StatementEntitySolver solver, StatementEntityInstance root) throws Exception, GRBException {
        Deque<StatementEntityInstance> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            StatementEntityInstance inst = queue.removeFirst();
            Solution sol = solver.solve(inst, this.componentLayoutTimeLimit);
            if (sol != null) {
                // Check whether components do not have too many empty spots.
                Set<Point> gaps = new HashSet<>();
                for (int x = 0; x < sol.getW(); ++x) {
                    int minY = sol.getH();
                    int maxY = 0;
                    for (int y = 0; y < sol.getH(); ++y) {
                        if (sol.hasCell(x, y)) {
                            if (y < minY) {
                                minY = y;
                            }
                            if (y > maxY) {
                                maxY = y;
                            }
                        }
                    }

                    for (int y = minY; y <= maxY; ++y) {
                        if (sol.getStatement(x, y).isEmpty()) {
                            gaps.add(new Point(x, y));
                        }
                    }
                }
                for (int y = 0; y < sol.getH(); ++y) {
                    int minX = sol.getW();
                    int maxX = 0;
                    for (int x = 0; x < sol.getW(); ++x) {
                        if (sol.hasCell(x, y)) {
                            if (x < minX) {
                                minX = x;
                            }
                            if (x > maxX) {
                                maxX = x;
                            }
                        }
                    }

                    for (int x = minX; x <= maxX; ++x) {
                        if (sol.getStatement(x, y).isEmpty()) {
                            gaps.add(new Point(x, y));
                        }
                    }
                }
                System.out.println("#gaps: " + gaps.size());
                if (gaps.size() <= sol.getInstance().numberOfStatements / 10) {
                    solutions.add(sol);
                    continue;
                }
            }

            // Too large or no optimal -> split
            GreedySplit splitInst = new GreedySplit(inst);
            ArrayList<StatementEntityInstance> parts = splitInst.findSplit(splitK, splitRatio);
            // Record deletions
            deletedNodes.addAll(splitInst.deletedEntities);

            // Enqueue parts
            queue.addAll(parts);
        }

        return solutions;
    }

    public PositionedSolution runBlockSets(StatementEntityInstance instance, PolygonType polygonType, Writer writer) {
        List<ConstraintModule> constraints = null;
        ObjectiveModule objective = null;
        int solutionType = -1;

        switch (polygonType) {
            case Arbitrary:
                // TODO: integrate with MosaicSets code
                break;
            case Orthoconvex:
                constraints = List.of(
                        new P0ValidEntityRowBounds(),
                        new P00DefineRowSpans(),
                        new P1ConsecutiveEntityRows(),
                        new P2ConnectedEntityRows(),
                        new P3VerticalConvexity(),
                        new P4StatementsInsideEntities(),
                        new P5OutsideNonMembers(),
                        new P6DisjointEntitiesDoNotOverlap(),
                        new P7SingleCellEntities(),
                        new P8MaxWidth(),
                        new P9MaxHeight(),
                        new P10StatementIsOnRowBooleans(),
                        new C1StatementsDistinctCoordinates());
                objective = new PolygonAreaDimensionsComplexity();
                solutionType = 1;
                break;
            case Nabla:
                constraints = List.of(
                        new P0ValidEntityRowBounds(),
                        new P00DefineRowSpans(),
                        new P1ConsecutiveEntityRows(),
                        new P2ConnectedEntityRows(),
                        new P3VerticalConvexity(),
                        new P4StatementsInsideEntities(),
                        new P5OutsideNonMembers(),
                        new P6DisjointEntitiesDoNotOverlap(),
                        new P7SingleCellEntities(),
                        new P8MaxWidth(),
                        new P9MaxHeight(),
                        new P10StatementIsOnRowBooleans(),
                        new P13aNestedRowBoundsNonIncreasing(),
                        new C1StatementsDistinctCoordinates());
                objective = new PolygonAreaDimensionsComplexity();
                solutionType = 1;
                break;
            case Gamma:
                constraints = List.of(
                        new P0ValidEntityRowBounds(),
                        new P00DefineRowSpans(),
                        new P1ConsecutiveEntityRows(),
                        new P2ConnectedEntityRows(),
                        new P3VerticalConvexity(),
                        new P4StatementsInsideEntities(),
                        new P5OutsideNonMembers(),
                        new P6DisjointEntitiesDoNotOverlap(),
                        new P7SingleCellEntities(),
                        new P8MaxWidth(),
                        new P9MaxHeight(),
                        new P10StatementIsOnRowBooleans(),
                        new P11EqualRowStart(),
                        new P13aNestedRowBoundsNonIncreasing(),
                        new C1StatementsDistinctCoordinates());
                objective = new PolygonAreaDimensionsComplexity();
                solutionType = 1;
                break;
            case Rectangle:
                constraints = List.of(
                        new C00NonNegativity(),
                        new C01UpperBound(),
                        new H1StatementsInsideEntities(),
                        new H2OutsideNonMembers(),
                        new H6DisjointEntitiesDoNotOverlap(),
                        new C1StatementsDistinctCoordinates(),
                        new C2SingleCellEntities(),
                        new H8MaxWidth(),
                        new H9MaxHeight(),
                        new H10Squareness());
                solutionType = 0;
                objective = new CompactSquareTopLeft();
                break;
        }

        StatementEntitySolver solver = new StatementEntitySolver(maxDimensions - 1, constraints, objective, solutionType);

        try {
            List<Solution> sols;
            sols = solveWithSplits(solver, instance);
            writer.write("Number of components: " + String.valueOf(sols.size()) + "\n");

            int totalNumberOfDuplicateEntities = 0;
            int numberOfDuplicatedEntities = 0;
            for (int eId = 0; eId < instance.numberOfEntities; ++eId) {
                int occurences = 0;
                for (var sol : sols) {
                    if (sol.getEntityIds().contains(eId)) {
                        ++occurences;
                    }
                }
                if (occurences > 1) {
                    ++numberOfDuplicatedEntities;
                    totalNumberOfDuplicateEntities += occurences - 1;
                }
            }
            writer.write("Total number of duplicate entities: " + totalNumberOfDuplicateEntities + "\n");
            writer.write("Number of duplicated entities: " + numberOfDuplicatedEntities + "\n");
            PositionedSolution finalLayout = SolutionPositioner.computeCompleteSolution((ArrayList<Solution>) sols, polygonType, componentArrangementTimeLimit);
            writer.write("Width: " + finalLayout.width + "\n");
            writer.write("Height: " + finalLayout.height + "\n");
            return finalLayout;
        } catch (Exception e) {
            e.printStackTrace();
            return PositionedSolution.EMPTY;
        }
    }

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 5 ) {
            System.out.println("Call this program with the following arguments: dataName outputName structure layoutTimeLimit arrangementTimeLimit.\n" +
                    "Time limits are in seconds. The structure parameter has one of the following values.\n" +
                    "0: arbitrary polygons\n" +
                    "1: orthoconvex polygons\n" +
                    "2: nabla-shapes (top-aligned)\n" +
                    "3: gamma-shapes (top- and left-aligned)\n" +
                    "4: rectangles");
            return;
        }


        File inputFile = new File(args[0]);
        File outputFile = new File(args[1]);

        PolygonType polygonType;

        double componentLayoutTimeLimit;
        double componentArrangementTimeLimit;

        try {
            int structure = Integer.parseInt(args[2]);
            if (structure < 0 || structure > 4) {
                System.out.println("As third argument, please provide an integer between 0 and 4.\nRun the program without arguments for more info.");
                return;
            }
            switch (structure) {
                case 0: polygonType = PolygonType.Arbitrary; break;
                case 1: polygonType = PolygonType.Orthoconvex; break;
                case 2: polygonType = PolygonType.Nabla; break;
                case 3: polygonType = PolygonType.Gamma; break;
                case 4: polygonType = PolygonType.Rectangle; break;
                default: throw new RuntimeException("Unknown structure argument: " + structure);
            }
        } catch(Exception e) {
            System.out.println("As third argument, please provide an integer between 0 and 4.\nRun the program without arguments for more info.");
            return;
        }

        try {
            componentLayoutTimeLimit = Integer.parseInt(args[3]);
            componentArrangementTimeLimit = Integer.parseInt(args[4]);
        } catch(Exception e) {
            System.out.println("As fourth and fifth argument, please provide time limits.\nRun the program without arguments for more info.");
            return;
        }

        var uniqueID = System.currentTimeMillis();
        var outputName = outputFile.getName().split("\\.(?=[^\\.]+$)")[0];
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("log_" + outputName + "_" + uniqueID + ".txt"))) {
            StatementEntityInstance instance = StatementEntityReader.readFromFile(inputFile.getPath());
            Orchestrator orchestrator = new Orchestrator(5, 1.0 / 3, componentLayoutTimeLimit, componentArrangementTimeLimit);
            PositionedSolution finalLayout = orchestrator.runBlockSets(instance, polygonType, writer);

            // Write result to file
            SolutionWriter.saveMultipleToFile(
                    finalLayout.solutions,
                    finalLayout.width,
                    finalLayout.height,
                    outputFile.getPath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
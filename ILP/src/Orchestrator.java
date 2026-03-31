import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

import com.gurobi.gurobi.GRBException;

import ilp.PolygonType;
import ilp.constraints.*;
import ilp.objective.CompactSquareTopLeft;
import ilp.objective.ObjectiveModule;
import ilp.objective.PolygonAreaDimensionsComplexity;
import ilp.solvers.MosaicSetsSolver;
import ilp.solvers.SolutionPositioner;
import ilp.solvers.OrthoconvexSolver;
import ilp.solvers.Solver;
import io.SolutionWriter;
import io.StatementEntityReader;
import io.StatsRecorder;
import model.PositionedSolution;
import model.Solution;
import model.StatementEntityInstance;
import split.ClusterSplit;
import split.GreedySplit;

public class Orchestrator {

    private final int splitK; // Maximum number of nodes to be deleted (usually 5)
    private final double splitRatio; // Coefficient that determines how wide is the range of acceptable components'
                                     // sizes produced from the split
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

    public List<Solution> solveWithSplits(Solver solver, StatementEntityInstance root,
                                          boolean rectEulerSplit, StatsRecorder stats, PolygonType polygonType) throws Exception, GRBException {
        // Store solved instances to record stats
        ArrayList<StatementEntityInstance> solvedInstances = new ArrayList<>();

        Deque<StatementEntityInstance> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            StatementEntityInstance inst = queue.removeFirst();
            long beforeSolve = System.nanoTime();
            int dimension = (int) (Math.ceil(Math.sqrt(inst.numberOfStatements)) + 1);
            if (polygonType == PolygonType.Nabla) {
                dimension += 1;
            } else if (polygonType == PolygonType.Gamma) {
                dimension += 2;
            } else if (polygonType == PolygonType.Rectangle) {
                dimension += 3;
            }
            Solution sol = solver.solve(inst, this.componentLayoutTimeLimit, dimension);
            long afterSolve = System.nanoTime();
            double layoutTimeS = (afterSolve - beforeSolve) / 1_000_000_000.0;
            stats.totalLayoutTime += layoutTimeS;

            if (sol != null) {
                solutions.add(sol);

                // Record shape stats for this solution
                stats.updateShapeStatsSingleComponent(sol);

                // Add solved instance to global solved list
                solvedInstances.add(inst);

                continue;
            }

            // Too large or no optimal -> split
            List<StatementEntityInstance> parts;

            // Record start time
            long beforeSplit = System.nanoTime();
            if (rectEulerSplit) {
                ClusterSplit splitter = new ClusterSplit(inst);
                String os = System.getProperty("os.name").toLowerCase();
                parts = splitter.splitWithPython(
                        os.contains("win") ?
                                Paths.get("ILP", "src", "split", "RectEuler-Split", ".venv", "Scripts", "python.exe") :
                                Paths.get("ILP", "src", "split", "RectEuler-Split", ".venv", "bin", "python")
                        ,
                        Paths.get("ILP", "src", "split", "RectEuler-Split", "cluster_split.py"),
                        Paths.get("ILP", "src", "split", "RectEuler-Split"),
                        2,
                        0,
                        5);
            } else {
                GreedySplit splitInst = new GreedySplit(inst);
                parts = splitInst.findSplit(splitK, splitRatio);
                // Record deletions
                deletedNodes.addAll(splitInst.deletedEntities);
            }
            // Measure total runtime for this split
            long afterSplit = System.nanoTime();
            double splitTimeS = (afterSplit - beforeSplit) / 1_000_000_000.0;
            stats.totalSplitTime += splitTimeS;


            // Enqueue parts
            queue.addAll(parts);
        }

        // Record final component stats
        stats.updateSplitComponentStats(solvedInstances);

        return solutions;
    }

    public PositionedSolution runBlockSets(StatementEntityInstance instance, PolygonType polygonType,
                                           StatsRecorder stats, boolean useRectEulerSplit, boolean mosaicSetsPerimeter) {
        List<ConstraintModule> constraints = null;
        ObjectiveModule objective = null;
        int solutionType = -1;

        switch (polygonType) {
            case Arbitrary:
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

        Solver solver;
        if (polygonType == PolygonType.Arbitrary) {
            solver = new MosaicSetsSolver(0, 0, false, mosaicSetsPerimeter);
        } else {
            solver = new OrthoconvexSolver(constraints, objective, solutionType);
        }

        try {
            List<Solution> sols;
            sols = solveWithSplits(solver, instance, useRectEulerSplit, stats, polygonType);
            sols.removeIf(solution -> solution.getEntityIds().isEmpty());

            long beforeArrange = System.nanoTime();
            PositionedSolution finalLayout = SolutionPositioner.computeCompleteSolution((ArrayList<Solution>) sols, polygonType, componentArrangementTimeLimit);
            long afterArrange = System.nanoTime();
            double arrangeTimeS = (afterArrange - beforeArrange) / 1_000_000_000.0;
            stats.totalArrangeTime += arrangeTimeS;

            stats.updateShapeStatsFinalLayout(finalLayout);

            return finalLayout;
        } catch (Exception e) {
            e.printStackTrace();
            return PositionedSolution.EMPTY;
        }
    }

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 6) {
            System.out.println("Call this program with the following arguments: dataName outputName statsName structure layoutTimeLimit arrangementTimeLimit.\n" +
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
        File statsFile = new File(args[2]);

        PolygonType polygonType;

        double componentLayoutTimeLimit;
        double componentArrangementTimeLimit;

        try {
            int structure = Integer.parseInt(args[3]);
            if (structure < 0 || structure > 4) {
                System.out.println("As fourth argument, please provide an integer between 0 and 4.\nRun the program without arguments for more info.");
                return;
            }
            switch (structure) {
                case 0:
                    polygonType = PolygonType.Arbitrary;
                    break;
                case 1:
                    polygonType = PolygonType.Orthoconvex;
                    break;
                case 2:
                    polygonType = PolygonType.Nabla;
                    break;
                case 3:
                    polygonType = PolygonType.Gamma;
                    break;
                case 4:
                    polygonType = PolygonType.Rectangle;
                    break;
                default:
                    throw new RuntimeException("Unknown structure argument: " + structure);
            }
        } catch(Exception e) {
            System.out.println("As fourth argument, please provide an integer between 0 and 4.\nRun the program without arguments for more info.");
            return;
        }

        try {
            componentLayoutTimeLimit = Integer.parseInt(args[4]);
            componentArrangementTimeLimit = Integer.parseInt(args[5]);
        } catch(Exception e) {
            System.out.println("As fifth and sixth argument, please provide time limits.\nRun the program without arguments for more info.");
            return;
        }

        var uniqueID = System.currentTimeMillis();
        var inputName = inputFile.getName().substring(0, inputFile.getName().lastIndexOf('.'));
        var outputName = outputFile.getName().split("\\.(?=[^\\.]+$)")[0];
        try {
            StatementEntityInstance instance = StatementEntityReader.readFromFile(inputFile.getPath());
            String[] runParams = {inputName, "BlockSets", polygonType.name()};
            StatsRecorder stats = new StatsRecorder(instance, runParams);

            Orchestrator orchestrator = new Orchestrator(5, 1.0 / 3, componentLayoutTimeLimit, componentArrangementTimeLimit);
            PositionedSolution finalLayout = orchestrator.runBlockSets(instance, polygonType, stats, false, false);

            // Write solution stats to file
            stats.appendToCsv(statsFile);

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

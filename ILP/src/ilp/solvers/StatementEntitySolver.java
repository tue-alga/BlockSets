package ilp.solvers;

import java.util.List;

import com.gurobi.gurobi.*;

import ilp.ModelContext;
import ilp.constraints.*;
import ilp.objective.*;
import model.Solution;
import model.StatementEntityInstance;

public class StatementEntitySolver {

    private final int dimensions;
    private final int gridMin = 0;
    private final int maxSizeSum = 8;
    private final double wTopLeft = 0.5;
    private final double wMaxExtents = 2.0;
    private final int solutionType;

    // Build the constraint set once
    private final List<ConstraintModule> constraints;

    private final ObjectiveModule objective;

    // Constructor with default constraints and objective (produces rectangle
    // solutions)
    public StatementEntitySolver(int dimensions, int solutionType) {
        this.dimensions = dimensions;
        this.constraints = List.of(
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
        this.objective = new CompactSquareTopLeft();
        this.solutionType = solutionType;
    }

    // Constructor that allows you to define your own list of constraints and
    // objective function
    public StatementEntitySolver(int dimensions, List<ConstraintModule> constraints, ObjectiveModule objective,
            int solutionType) {
        this.dimensions = dimensions;
        this.constraints = constraints;
        this.objective = objective;
        this.solutionType = solutionType;
    }

    /**
     * Pure solve for a single instance.
     * 
     * @return Solution if optimal, else null (caller decides to split).
     */
    public Solution solve(StatementEntityInstance inst, double timeLimit) throws Exception, GRBException {
        int maxCells = (dimensions + 1) * (dimensions + 1);
        if (inst.numberOfStatements > maxCells) {
            System.out.println("Instance too large");
            return null;
        }

        try (ModelContext ctx = new ModelContext(inst, dimensions, gridMin, maxSizeSum, wTopLeft, wMaxExtents,
                solutionType)) {
            // Add constraints
            for (ConstraintModule c : constraints)
                c.add(ctx);

            // Objective
            objective.apply(ctx);

            ctx.model.set(GRB.DoubleParam.Heuristics, 0.3);
            // ctx.model.set(GRB.IntParam.MIPFocus, GRB.MIPFOCUS_FEASIBILITY);

            // Solve
            // ctx.model.optimize();
            // int status = ctx.model.get(GRB.IntAttr.Status);
            // if (status != GRB.Status.OPTIMAL) {
            // return null;
            // }

            // Set time limit
             ctx.model.set(GRB.DoubleParam.TimeLimit, timeLimit);

            // Set callback to save solutions when found
            // SaveSolutionCallback cb = new SaveSolutionCallback(ctx, solutionType);
            // ctx.model.setCallback(cb);  

            // Solve
            ctx.model.optimize();
            int status = ctx.model.get(GRB.IntAttr.Status);
            int solCount = ctx.model.get(GRB.IntAttr.SolCount);

            // If no feasible solution exists:
            if (status == GRB.Status.INFEASIBLE ||
                    status == GRB.Status.INF_OR_UNBD ||
                    solCount == 0) {

                return null;
            }

            // Extract and return
            switch (solutionType) {
                case 0:
                    return SolutionExtractor.extractRectangleSolution(ctx);
                case 1:
                    return SolutionExtractor.extractPolygonSolution(ctx);
                default:
                    throw new Exception("Unknown solution type");
            }
        }
    }
}
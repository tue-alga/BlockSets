package ilp.solvers;

import com.gurobi.gurobi.GRBException;
import model.Solution;
import model.StatementEntityInstance;

public class CombinedSolver implements Solver {
    Solver initialSolver;
    NewSolver newSolver;

    public CombinedSolver(Solver initialSolver, NewSolver newSolver) {
        this.initialSolver = initialSolver;
        this.newSolver = newSolver;
    }

    @Override
    public Solution solve(StatementEntityInstance inst, double timeLimit, int dimensions) throws Exception, GRBException {
        var initialSolution = initialSolver.solve(inst, timeLimit / 2, dimensions);
        return newSolver.warmSolve(inst, timeLimit / 2, dimensions, initialSolution);
    }
}

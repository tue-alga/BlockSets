package ilp.solvers;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import model.Solution;
import model.StatementEntityInstance;
import org.jgrapht.alg.util.Pair;

public class CombinedSolver implements Solver {
    Solver initialSolver;
    NewSolver newSolver;

    public CombinedSolver(Solver initialSolver, NewSolver newSolver) {
        this.initialSolver = initialSolver;
        this.newSolver = newSolver;
    }

    @Override
    public Pair<Solution, Integer> solve(StatementEntityInstance inst, double timeLimit, int dimensions) throws Exception, GRBException {
        var pair = initialSolver.solve(inst, timeLimit / 2, dimensions);
        var initialSolution = pair.getFirst();
        var status = pair.getSecond();
        if (status == GRB.INFEASIBLE) {
            return new Pair(null, status);
        }
        return newSolver.warmSolve(inst, timeLimit / 2, dimensions, initialSolution);
    }
}

package ilp.solvers;

import com.gurobi.gurobi.GRBException;
import model.Solution;
import model.StatementEntityInstance;

import java.util.List;

public interface Solver {
    /**
     * Pure solve for a single instance.
     *
     * @return Solution if optimal, else null (caller decides to split).
     */
    public Solution solve(StatementEntityInstance inst, double timeLimit) throws Exception, GRBException;
}

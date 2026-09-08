package ilp.solvers;

import com.gurobi.gurobi.GRBException;
import io.StatsRecorder;
import model.Solution;
import model.StatementEntityInstance;
import org.jgrapht.alg.util.Pair;

import java.util.List;

public interface Solver {
    /**
     * Pure solve for a single instance.
     *
     * @return Solution if optimal, else null (caller decides to split).
     */
    public Pair<Solution, Integer> solve(StatementEntityInstance inst, double timeLimit, int dimensions) throws Exception, GRBException;
}

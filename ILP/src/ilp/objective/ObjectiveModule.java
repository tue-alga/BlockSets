package ilp.objective;
import com.gurobi.gurobi.GRBException;
import ilp.ModelContext;

public interface ObjectiveModule {
  void apply(ModelContext ctx) throws GRBException;
}


package ilp.constraints;

import com.gurobi.gurobi.GRBException;
import ilp.ModelContext;

public interface ConstraintModule {
  void add(ModelContext ctx) throws GRBException;

  default String name() {
    return getClass().getSimpleName();
  }
}
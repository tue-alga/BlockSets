package ilp;
import com.gurobi.gurobi.GRBEnv;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBModel;

import ilp.variables.Vars;
import ilp.variables.VarsFactory;
import model.StatementEntityInstance;

import java.util.*;

public final class ModelContext implements AutoCloseable {
  public final StatementEntityInstance inst;

  public final GRBEnv env;
  public final GRBModel model;

  // IDs and index lookups
  public final ArrayList<Integer> entityIds;
  public final List<Integer> statementIds;
  public final Map<Integer,Integer> entityIdToIdx;
  public final Map<Integer,Integer> statementIdToIdx;

  // Vars (created via VarsFactory)
  public final Vars v;

  // Config/params
  public final int dimensions;
  public final int gridMin;
  public final int gridMax;
  public final int bigM;
  public final int maxSizeSum;
  public final double wTopLeft;
  public final double wMaxExtents;

  // Model Type: - 0 for rectangles
  //             - 1 for convex polygons
  public ModelContext(StatementEntityInstance inst,
                      int dimensions,
                      int gridMin,
                      int maxSizeSum,
                      double wTopLeft,
                      double wMaxExtents, int modelType) throws GRBException {
    this.inst = inst;
    this.dimensions = dimensions;
    this.gridMin = gridMin;
    this.gridMax = dimensions;
    this.bigM = gridMax + 1;
    this.maxSizeSum = maxSizeSum;
    this.wTopLeft = wTopLeft;
    this.wMaxExtents = wMaxExtents;

    this.entityIds = new ArrayList<>(inst.entities.keySet());
    this.statementIds = new ArrayList<>(inst.statements.keySet());

    this.entityIdToIdx = new HashMap<>();
    for (int i = 0; i < entityIds.size(); i++) entityIdToIdx.put(entityIds.get(i), i);

    this.statementIdToIdx = new HashMap<>();
    for (int i = 0; i < statementIds.size(); i++) statementIdToIdx.put(statementIds.get(i), i);

    this.env = new GRBEnv();
    this.model = new GRBModel(env);

    this.v = VarsFactory.create(model, entityIds.size(), statementIds.size(), gridMin, gridMax, modelType);
  }

  @Override public void close() throws GRBException {
    model.dispose();
    env.dispose();
  }
}

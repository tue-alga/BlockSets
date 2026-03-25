package ilp.solvers.mosaicsets;

import com.gurobi.gurobi.*;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.jgrapht.graph.Multigraph;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.Map.Entry;

/**
 * Class that contains methods for computing MosaicSets
 *
 * @param <T> Defines the type of the set elements. Needs to implement
 *            Comparable for detecting identical elements
 */
public class SetEmbedder<T extends Comparable<T>> {

  // the graph where the sets will be embedded
  public Multigraph<Point2D.Double, DefaultEdge> targetGraph;

  // a list of sets, each containing multiple integers
  public List<Set<T>> sets;

  public Map<Integer, List<Line2D.Double>> setsToSelectedArcs;

  // Parameter for enabling boundary length minimization
  public static boolean MINIMIZE_BOUNDARIES = false;
  // Parameter for relaxing project contiguity
  public static boolean RELAX_PROJECT_CONTIGUITY = false;
  // Parameter for fixing the area of subsequent solution to the area of the
  // first solution
  public static boolean LIMIT_AREA_TO_FIRST_SOLUTION = true;
  // Parameter if squared distance should be used
  public static boolean DIST_SQUARED = false;
  // Number of instances
  public int nInst = 0;

  /**
   * Constructor to set the arguments of the algorithm
   * 
   * @param targetGraph Targetgraph of the embedding
   * @param sets        List of sets
   */
  public SetEmbedder(Multigraph<Point2D.Double, DefaultEdge> targetGraph,
      List<Set<T>> sets) {
    this.targetGraph = targetGraph;
    this.sets = sets;
  }

  /**
   * Constructor to set the arguments of the algorithm
   * 
   * @param targetGraph
   * @param institutes
   * @param projects
   */
  public SetEmbedder(Multigraph<Point2D.Double, DefaultEdge> targetGraph,
      List<Set<T>> institutes, List<Set<T>> projects) {
    this.targetGraph = targetGraph;
    this.sets = new ArrayList<Set<T>>();
    this.sets.addAll(institutes);
    this.sets.addAll(projects);
    this.nInst = institutes.size();
  }

  /**
   * Method to perform the optimization.
   * 
   * @return Assignments of graph nodes to set elements
   */
  public Map<Point2D.Double, T> optimize(double timeLimit, String resultPath) {
    return this.optimize(timeLimit, 0, null, false, resultPath);
  }

  /**
   * Method to perform the optimization.
   * 
   * @param  mipGap Maximum gap for optimization
   * @return        Assignments of graph nodes to set elements
   */
  public Map<Point2D.Double, T> optimize(double timeLimit, double mipGap, String resultPath) {
    return this.optimize(timeLimit, mipGap, null, false, resultPath);
  }

  /**
   * Method to perform the optimization.
   * 
   * @param  setCenters Predefined set centers in the same order as the sets
   * @return            Assignments of graph nodes to set elements
   */
  public Map<Point2D.Double, T> optimize(double timeLimit, List<Point2D.Double> setCenters, String resultPath) {
    return this.optimize(timeLimit, 0, setCenters, false, resultPath);
  }

  /**
   * Method to perform the optimization.
   * 
   * @param  mipGap     Maximum gap for optimization
   * @param  setCenters Predefined set centers in the same order as the sets
   * @return            Assignments of graph nodes to set elements
   */
  public Map<Point2D.Double, T> optimize(double timeLimit, double mipGap,
      List<Point2D.Double> setCenters, String resultPath) {
    return this.optimize(timeLimit, mipGap, setCenters, false, resultPath);
  }

  /**
   * Method to perform the optimization.
   * 
   * @param  mipGap        Maximum gap for optimization
   * @param  setCenters    Predefined set centers in the same order as the sets
   * @param  subsequentRun Value if the previous result should be loaded for
   *                       further optimization
   * @return               Assignments of graph nodes to set elements
   */
  public Map<Point2D.Double, T> optimize(double timeLimit, double mipGap,
      List<Point2D.Double> setCenters, boolean subsequentRun, String resultPath) {
    if (!setCenters.isEmpty())
      assert setCenters.size() == sets.size();

    int n = this.targetGraph.vertexSet().size();

    Point2D.Double topLeft = new Point2D.Double(Double.MAX_VALUE,
        Double.MAX_VALUE);
    Point2D.Double bottomRight = new Point2D.Double(Double.MIN_VALUE,
        Double.MIN_VALUE);

    // compute centroid of target graph as reference point for measuring
    // compactness
    double xMid = 0.0;
    double yMid = 0.0;
    for (Point2D.Double p : this.targetGraph.vertexSet()) {
      xMid += p.getX();
      yMid += p.getY();
      if (smaller(p, topLeft))
        topLeft = p;
      if (smaller(bottomRight, p))
        bottomRight = p;
    }
    xMid /= n;
    yMid /= n;

    Point2D.Double pMid = new Point2D.Double(xMid, yMid);

    // compute a map with the following properties
    // key: each element contained in at least one of the sets
    // val: the sets where that element occurs
    HashMap<T, ElementWithSets<T>> allElementsWithSets = new HashMap<>();
    for (int i = 0; i < sets.size(); i++) { // iterate all sets
      Set<T> set = sets.get(i);
      for (T element : set) { // iterate all elements of set i
        ElementWithSets<T> setsOfElement = allElementsWithSets.get(element);
        if (setsOfElement == null) {
          setsOfElement = new ElementWithSets<T>(element);
          allElementsWithSets.put(element, setsOfElement);
        }
        setsOfElement.setIDs.add(i);
      }
    }

    ArrayList<ElementWithSets<T>> a = new ArrayList<>();
    a.addAll(allElementsWithSets.values());
    Collections.sort(a);

    // compute a map with the following properties
    // key: a representative set element for each cluster, where elements within
    // a cluster have the same set memberships
    // val: the number of set elements of that cluster
    Map<T, Integer> elementsWithWeights = new HashMap<>();
    Map<T, List<T>> elementsToOriginal = new HashMap<>();

    List<T> orig = null;
//    System.out.println("Elements With Sets");
    ElementWithSets<T> pred = null;

    int counter = 0;
    for (ElementWithSets<T> ews : a) {
//      System.out.println(ews);
      if (!ews.equals(pred)) {
        if (pred != null) {
          elementsWithWeights.put(pred.element, counter);
          elementsToOriginal.put(pred.element, orig);
        }
        counter = 1;
        pred = ews;
        orig = new LinkedList<>();
        orig.add(ews.element);
      } else {
        counter++;
        orig.add(ews.element);
      }
    }
    if (pred != null) {
      elementsWithWeights.put(pred.element, counter);
      elementsToOriginal.put(pred.element, orig);
    }

    // find nice centers, i.e., elements that do not share their set memberships
    // with others
    Set<T> niceCenters = new HashSet<>();
    for (Entry<T, Integer> eww : elementsWithWeights.entrySet()) {
      if (eww.getValue() == 1) {
        niceCenters.add(eww.getKey());
        System.out.println(eww.getKey() + " " + eww.getValue());
      }
    }

    // define for each set its center
    List<T> centers = new ArrayList<T>();
    for (Iterable<T> set : sets) {
      T center = set.iterator().next();
      for (T element : set) {
        if (niceCenters.contains(element)) {
          center = element;
          break;
        }
      }
      centers.add(center);
    }

    // report centers
//    for (int i = 0; i < centers.size(); i++) {
//      System.out.println("set " + i + " " + centers.get(i));
//    }

    // compute elementsWithWeights again, but this time keep centers alone
    elementsWithWeights = new HashMap<>();
    elementsToOriginal = new HashMap<>();
    pred = null;
    counter = 0;
    for (ElementWithSets<T> ews : a) {
      // centers are simply skipped (and kept as singletons)
      if (centers.contains(ews.element)) {
        elementsWithWeights.put(ews.element, 1);
        List<T> tmp = new LinkedList<>();
        tmp.add(ews.element);
        elementsToOriginal.put(ews.element, tmp);
        continue;
      }
      if (!ews.equals(pred)) {
        if (pred != null) {
          elementsWithWeights.put(pred.element, counter);
          elementsToOriginal.put(ews.element, orig);
        }
        counter = 1;
        pred = ews;
        orig = new LinkedList<>();
        orig.add(ews.element);
        elementsToOriginal.put(ews.element, orig);
      } else {
        counter++;
        orig.add(ews.element);
      }
    }
    if (pred != null) {
      elementsWithWeights.put(pred.element, counter);
      elementsToOriginal.put(pred.element, orig);
    }

//    System.out.println("Elements With Weights");
//    for (Entry<T, Integer> eww : elementsWithWeights.entrySet()) {
//      System.out.println(eww.getKey() + ":" + eww.getValue());
//    }

    // points associated with node ids
    Map<Point2D.Double, Integer> pointsToIDs = new HashMap<Point2D.Double, Integer>();
    Map<Integer, Point2D.Double> IDsToPoints = new HashMap<Integer, Point2D.Double>();

    // set elements associated with node ids
    Map<T, Integer> elementsToIDs = new HashMap<>();

    // graph to do the optimization
    DirectedWeightedMultigraph<Integer, DefaultEdge> algoGraph = new DirectedWeightedMultigraph<>(
        DefaultEdge.class);

    // algoGraph: one node for each node of the target graph
    int i = 0;
    for (Point2D.Double v : this.targetGraph.vertexSet()) {
      algoGraph.addVertex(i);
      pointsToIDs.put(v, i);
      IDsToPoints.put(i, v);
      i++;
    }

    // algoGraph: one node for each cluster of set elements
    // algoGraph: one edge for each cluster of set elements and each node of
    // target graph
    Map<DefaultEdge, String> edgesToStrings = new HashMap<DefaultEdge, String>();
    for (T element : elementsWithWeights.keySet()) {
      algoGraph.addVertex(i);
      elementsToIDs.put(element, i);

      // set->position edges
      for (Point2D.Double v : this.targetGraph.vertexSet()) {
        DefaultEdge eNew = algoGraph.addEdge(i, pointsToIDs.get(v));
        edgesToStrings.put(eNew, "(" + i + "-" + pointsToIDs.get(v) + ")");
      }
      i++;
    }

    // ids of nodes corresponding to cluster centers
    ArrayList<Integer> centerIDs = new ArrayList<Integer>();
    for (T c : centers) {
      centerIDs.add(elementsToIDs.get(c));
    }

    // algoGraph: two directed edges for each edge of target graph
    for (DefaultEdge e : this.targetGraph.edgeSet()) {
      // position->position edges
      Integer u = pointsToIDs.get(this.targetGraph.getEdgeSource(e));
      Integer v = pointsToIDs.get(this.targetGraph.getEdgeTarget(e));

      DefaultEdge eNew1 = algoGraph.addEdge(u, v);
      edgesToStrings.put(eNew1, "(" + u + "-" + v + ")");

      DefaultEdge eNew2 = algoGraph.addEdge(v, u);
      edgesToStrings.put(eNew2, "(" + v + "-" + u + ")");
    }

    // sets based on node ids
    List<HashSet<Integer>> nodeSets = new ArrayList<HashSet<Integer>>();
    for (Iterable<T> set : sets) {
      HashSet<Integer> nodes = new HashSet<Integer>();
      for (T element : set) {
        if (elementsWithWeights.containsKey(element)) {
          nodes.add(elementsToIDs.get(element));
        }
      }
      nodeSets.add(nodes);
    }

    // solution that will be returned
    HashMap<Point2D.Double, T> pointsToElements = new HashMap<>();

    /*
     * set up model
     */
    try {
      // Maps the variables of each set to the edge
      Map<DefaultEdge, List<GRBVar>> edgesToVars = new HashMap<>();
      // Maps the flow variables of each set to the edge
      Map<DefaultEdge, List<GRBVar>> edgesToFVars = new HashMap<>();

      GRBEnv env = new GRBEnv();
      // Maximum time limit of one hour when minimizing boundary length
      env.set(GRB.DoubleParam.TimeLimit, timeLimit);
      env.set(GRB.DoubleParam.MIPGap, mipGap);

      GRBModel model = new GRBModel(env);

      // variables indicating selection of edges
      for (DefaultEdge e : algoGraph.edgeSet()) {

        ArrayList<GRBVar> vars = new ArrayList<GRBVar>();
        ArrayList<GRBVar> f_vars = new ArrayList<GRBVar>();

        // edges of target graph can be used for different sets
        if (algoGraph.getEdgeSource(e) < n && algoGraph.getEdgeTarget(e) < n) {
          for (int setIndex = 0; setIndex < sets.size(); setIndex++) {
            // Add edges only in one direction / undirected
            if (MINIMIZE_BOUNDARIES
                && algoGraph.getEdgeSource(e) < algoGraph.getEdgeTarget(e))
              vars.add(model.addVar(0, 1, -1, GRB.BINARY, null));

            int setSize = sets.get(setIndex).size();

            GRBVar f_var = model.addVar(0, setSize - 1, 0, GRB.INTEGER,
                "f_" + edgesToStrings.get(e) + "_" + setIndex);
            f_vars.add(f_var);
          }
        } else {
          // edges incident to set elements can be used independent of set
          Point2D.Double target = IDsToPoints.get(algoGraph.getEdgeTarget(e));

          double distance = target.distance(pMid);
          double distanceCenter = 0;
          // Distance has no influence when optimizing boundaries
          if (MINIMIZE_BOUNDARIES)
            distance *= 0;

          GRBVar var = null;

          if (setCenters == null) {
            var = model.addVar(0, 1, distance, GRB.BINARY,
                "x_" + edgesToStrings.get(e));
          } else {
            // Used for 2 step process with given centers.
            // Sum up distance to every center where element is part of the set
            for (int setIndex = 0; setIndex < sets.size(); setIndex++) {
              if (nodeSets.get(setIndex).contains(algoGraph.getEdgeSource(e))
                  && !MINIMIZE_BOUNDARIES) {
                double exponent = DIST_SQUARED ? 2d : 1d;
                double d = Math.pow(target.distance(setCenters.get(setIndex)),
                    exponent);
                distanceCenter += d;
              }
            }
          }
          for (int setIndex = 0; setIndex < sets.size(); setIndex++) {
            Integer s = algoGraph.getEdgeSource(e);
            // Source node is part of current set
            if (nodeSets.get(setIndex).contains(s)) {
              // Add the variable only one time and use it for all edges/sets
              if (var == null)
                var = model.addVar(0, 1, distanceCenter, GRB.BINARY,
                    "x_" + edgesToStrings.get(e));
              vars.add(var);

            } else {
              vars.add(null);
            }
          }
        }

        edgesToVars.put(e, vars);
        edgesToFVars.put(e, f_vars);
      }
      model.update();
      model.setObjective(model.getObjective(), GRB.MINIMIZE);

      // for each cluster of set elements: select exactly as many edges as the
      // cluster's weight
      for (T element : elementsWithWeights.keySet()) {
        GRBLinExpr expr = new GRBLinExpr();
        Integer id = elementsToIDs.get(element);
        for (DefaultEdge e : algoGraph.outgoingEdgesOf(id)) {
          expr.addTerm(1.0, getFirstNonNull(edgesToVars.get(e)));
        }
        model.addConstr(expr, GRB.EQUAL, elementsWithWeights.get(element),
            "exactly1_" + id);
      }

      // for each node of the target graph: select at most one edge from a set
      // element
      for (Point2D.Double v : targetGraph.vertexSet()) {
        GRBLinExpr expr = new GRBLinExpr();
        Integer id = pointsToIDs.get(v);
        for (DefaultEdge e : algoGraph.incomingEdgesOf(id)) {
          if (algoGraph.getEdgeSource(e) >= n) {
            expr.addTerm(1.0, getFirstNonNull(edgesToVars.get(e)));
          }
        }
        model.addConstr(expr, GRB.LESS_EQUAL, 1.0, "atmost1_" + id);
      }

      // for each node of the target graph and each set: number of outgoing
      // edges is equal to number of incoming edges from
      // non-center elements (checked)
      for (int setID = 0; setID < sets.size(); setID++) {
        int m = this.sets.get(setID).size();
        Set<Integer> set = nodeSets.get(setID);
        for (Integer v : algoGraph.vertexSet()) {
          if (v < n) {
            // -> v belongs to target graph
            GRBLinExpr outFLow = new GRBLinExpr();

            for (DefaultEdge e : algoGraph.outgoingEdgesOf(v)) {
              GRBVar fvar = edgesToFVars.get(e).get(setID);
              outFLow.addTerm(1.0, fvar);
            }

            GRBLinExpr inFlow = new GRBLinExpr();
            GRBLinExpr xi = new GRBLinExpr();
            GRBVar wi = null;
            for (DefaultEdge e : algoGraph.incomingEdgesOf(v)) {
              int u = algoGraph.getEdgeSource(e);
              if (u < n) {

                GRBVar fvar = edgesToFVars.get(e).get(setID);
                inFlow.addTerm(1.0, fvar);

              } else if (set.contains(u)) {
                GRBVar var = edgesToVars.get(e).get(setID);
                xi.addTerm(1.0, var);
                if (centerIDs.get(setID) == u) {
                  wi = var;
                }
              }

            }
            if (MINIMIZE_BOUNDARIES)
              for (DefaultEdge e : algoGraph.incomingEdgesOf(v)) {
                int u = algoGraph.getEdgeSource(e);

                if (u < n && u < v) {
                  // xj represents the selection of the source vertex of the
                  // edge
                  GRBLinExpr xj = new GRBLinExpr();
                  for (DefaultEdge eu : algoGraph.incomingEdgesOf(u)) {
                    int w = algoGraph.getEdgeSource(eu);
                    if (w >= n && set.contains(w)) {
                      xj.addTerm(1, edgesToVars.get(eu).get(setID));
                    }
                  }
                  model.addConstr(edgesToVars.get(e).get(setID), GRB.LESS_EQUAL,
                      xi, null);
                  model.addConstr(edgesToVars.get(e).get(setID), GRB.LESS_EQUAL,
                      xj, null);
                }
              }

            // Expressions for shirabe model
            GRBLinExpr flowDif = new GRBLinExpr();
            flowDif.add(outFLow);
            flowDif.multAdd(-1.0, inFlow);
            GRBLinExpr rightSide = new GRBLinExpr();
            rightSide.add(xi);
            rightSide.addTerm(-m, wi);

            GRBLinExpr maxFlow = new GRBLinExpr();
            maxFlow.multAdd(m - 1, xi);

            if (!RELAX_PROJECT_CONTIGUITY || setID < nInst)
              model.addConstr(inFlow, GRB.LESS_EQUAL, maxFlow,
                  "flowConst_" + v);
            model.addConstr(flowDif, GRB.EQUAL, rightSide, "flowInc_" + v);

          }
        }
      }

      // for each edge of the target graph and each set select edge only for
      // that set if both of its nodes are selected for it
      for (DefaultEdge e : algoGraph.edgeSet()) {
        Integer u = algoGraph.getEdgeSource(e);
        Integer v = algoGraph.getEdgeTarget(e);
        if (u < n && v < n) {
          // -> e belongs to target graph
          List<GRBVar> edgeVars = edgesToVars.get(e);
          for (int setID = 0; setID < edgeVars.size(); setID++) {
            // select edge e only for a set if its target v is selected for same
            // set
            GRBLinExpr expr2 = new GRBLinExpr();
            for (DefaultEdge f : algoGraph.incomingEdgesOf(v)) {
              if (nodeSets.get(setID).contains(algoGraph.getEdgeSource(f))) {
                expr2.addTerm(1.0, edgesToVars.get(f).get(setID));
              }
            }
            model.addConstr(expr2, GRB.GREATER_EQUAL, edgeVars.get(setID),
                "onlywithin");
          }
        }
      }

      // edges of target graph
      List<DefaultEdge> targetEdges = new ArrayList<DefaultEdge>();
      for (DefaultEdge e : algoGraph.edgeSet()) {
        Integer u = algoGraph.getEdgeSource(e);
        Integer v = algoGraph.getEdgeTarget(e);
        if (u < n && v < n) { // -> e belongs to target graph
          targetEdges.add(e);
        }
      }

      // Write model
      model.write(resultPath + "model.lp");

      // Load previous run
      if (subsequentRun) {
        model.read(resultPath + "model.mst");
        if (LIMIT_AREA_TO_FIRST_SOLUTION) {
          GRBLinExpr exp = new GRBLinExpr();
          Set<Point2D.Double> points = pointsToIDs.keySet();
          for (int setId = 0; setId < setsToSelectedArcs.size(); setId++) {
            for (Line2D.Double arc : setsToSelectedArcs.get(setId)) {
              if (points.contains(arc.getP1()))
                points.remove(arc.getP1());
              if (points.contains(arc.getP2()))
                points.remove(arc.getP2());
            }
          }
          for (Point2D.Double point : points) {
            Integer id = pointsToIDs.get(point);
            for (DefaultEdge edge : algoGraph.incomingEdgesOf(id)) {
              if (id < n && algoGraph.getEdgeSource(edge) >= n)
                exp.addTerm(1, getFirstNonNull(edgesToVars.get(edge)));
            }
            model.addConstr(exp, GRB.EQUAL, 0, "do_not_use_node_" + id);
          }
        }
      }
      // optimize
      model.optimize();

      int status = model.get(GRB.IntAttr.Status);
      int solCount = model.get(GRB.IntAttr.SolCount);
      if (status == GRB.Status.INFEASIBLE ||
              status == GRB.Status.INF_OR_UNBD ||
              solCount == 0) {

        return null;
      }
      if (!subsequentRun)
        model.write(resultPath + "model.mst");

      /*
       * construct solution
       */
      int nAssignments = 0;
      for (T element : elementsWithWeights.keySet()) {
        Integer node = elementsToIDs.get(element);
        for (DefaultEdge e : algoGraph.outgoingEdgesOf(node)) {
          GRBVar var = getFirstNonNull(edgesToVars.get(e));
          if (var.get(GRB.DoubleAttr.X) > 0.99) {
            Integer node2 = algoGraph.getEdgeTarget(e);
            pointsToElements.put(IDsToPoints.get(node2),
                elementsToOriginal.get(element).remove(0));
            nAssignments++;
          }
        }
      }
      System.out.println("nAssignments " + nAssignments);

      setsToSelectedArcs = new HashMap<Integer, List<Line2D.Double>>();
      for (int setId = 0; setId < sets.size(); setId++) {
        List<Line2D.Double> al = new ArrayList<Line2D.Double>();
        setsToSelectedArcs.put(setId, al);

        for (DefaultEdge e : algoGraph.edgeSet()) {
          Integer u = algoGraph.getEdgeSource(e);
          Integer v = algoGraph.getEdgeTarget(e);
          if (u < n && v < n) {
            GRBVar var = edgesToFVars.get(e).get(setId);

            boolean relax = true;
            if (RELAX_PROJECT_CONTIGUITY) {
              double sumIn = 0, sumOut = 0;
              for (DefaultEdge eIn : algoGraph.incomingEdgesOf(u))
                if (algoGraph.getEdgeSource(eIn) < n)
                  sumIn += edgesToFVars.get(eIn).get(setId)
                      .get(GRB.DoubleAttr.X);
              for (DefaultEdge eOut : algoGraph.outgoingEdgesOf(u))
                if (algoGraph.getEdgeTarget(eOut) < n)
                  sumOut += edgesToFVars.get(eOut).get(setId)
                      .get(GRB.DoubleAttr.X);
              double diff = sumOut - sumIn;

              relax = sumOut > sumIn
                  || -diff > (this.sets.get(setId).size() - 1.01);
            }

            if (var.get(GRB.DoubleAttr.X) > 0.99 && relax) {
              Point2D.Double p1 = IDsToPoints.get(u);
              Point2D.Double p2 = IDsToPoints.get(v);
              Line2D.Double l12 = new Line2D.Double(p1, p2);
              al.add(l12);
            }
          }
        }
      }

    } catch (GRBException e1) {
      e1.printStackTrace();
    }

    return pointsToElements;
  }

  /**
   * Retrieves the first variable of the iterable
   * 
   * @param  variables_it Iterator of variables
   * @return              Gurobi variable or null, if no non null variable is
   *                      present
   */
  private static GRBVar getFirstNonNull(Iterable<GRBVar> variables_it) {
    for (GRBVar tmp : variables_it) {
      if (tmp != null) {
        return tmp;
      }
    }
    return null;
  }

  /**
   * Method for checking if a point is lower left than another point
   * 
   * @param  p     Point of interest
   * @param  other Other point
   * @return       True if p is lower left of other
   */
  private static boolean smaller(Point2D.Double p, Point2D.Double other) {
    if (p.x < other.x)
      return true;
    else if (p.x > other.x)
      return false;
    if (p.y < other.y)
      return true;
    return false;
  }
}

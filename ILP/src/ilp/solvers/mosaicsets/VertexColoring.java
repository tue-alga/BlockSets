package ilp.solvers.mosaicsets;

import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.Multigraph;
import org.jgrapht.graph.SimpleGraph;

import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class VertexColoring<T> {

  private Map<Point2D.Double, T> pointsToElements;
  private List<Set<T>> projects;
  private Multigraph<Point2D.Double, DefaultEdge> gridGraph;
  private SimpleGraph<Integer, DefaultEdge> conflictGraph;
  private int V;
  private int[] color;
  private int numOfColors;
  private int numNeighbors;

  public VertexColoring(List<Set<T>> projects, Map<Point2D.Double, T> solution,
      Multigraph<Point2D.Double, DefaultEdge> graph, int gridType) {
    this.projects = projects;
    this.pointsToElements = solution;
    this.gridGraph = graph;
    this.conflictGraph = getConflictGraph();
    this.V = this.conflictGraph.vertexSet().size();
    this.color = new int[V];
    if (gridType == Grid.TYPE_HEX) {
      this.numNeighbors = 6;
    } else if (gridType == Grid.TYPE_SQUARE) {
      this.numNeighbors = 4;
    }
  }

  public int getColor(int i) {
    return color[i];
  }

  /**
   * function searches for proper k-coloring with smallest number k of colors
   */
  public void colorGraph() {
    for (int k = 1; k <= V; k++) {
      this.numOfColors = k;

      if (solveColoring(0))
        break;
    }
  }

  /**
   * recursive brute-force strategy for finding k-coloring
   * 
   * @param  i : current vertex
   * @return
   */
  private boolean solveColoring(int i) {
    if (i == V) {
      if (isFeasible()) {
        return true;
      }
      return false;
    }
    // try all colors
    for (int j = 0; j < numOfColors; j++) {
      color[i] = j;
      if (solveColoring(i + 1))
        return true;

      color[i] = 0;
    }

    return false;
  }

  /**
   * function checks feasibility of current coloring
   * 
   * @return
   */
  private boolean isFeasible() {
    for (int i = 0; i < V; i++)
      for (int j = i + 1; j < V; j++)
        if (conflictGraph.getEdge(i, j) != null && color[j] == color[i])
          return false;
    return true;
  }

  /**
   * print coloring
   */
  public void printColorArray() {
    System.out.println("Solution colors are: ");
    for (int i = 0; i < color.length; i++) {
      System.out.println(color[i]);
    }
  }

  /**
   * get conflict graph of projects in solution
   * 
   * @return
   */
  public SimpleGraph<Integer, DefaultEdge> getConflictGraph() {
    SimpleGraph<Integer, DefaultEdge> conflictGraph = new SimpleGraph<>(
        DefaultEdge.class);

    if (projects != null && pointsToElements != null) {
      for (Entry<Point2D.Double, T> entry : pointsToElements.entrySet()) { // for
                                                                           // each
                                                                           // vertex
        int set1ID = 0;
        for (Collection<T> set1 : projects) {
          conflictGraph.addVertex(set1ID);
          int set2ID = 0;
          for (Collection<T> set2 : projects) {
            conflictGraph.addVertex(set2ID);
            if (set1 != set2) {
              // if set1 and set2 are intersecting
              if (set1.contains(entry.getValue())
                  && set2.contains(entry.getValue())) {
                List<Point2D.Double> neighbors = Graphs
                    .neighborListOf(gridGraph, entry.getKey());
                // if the conflict occurs at an edge node
                if (neighbors.size() != numNeighbors) {
                  conflictGraph.addEdge(set1ID, set2ID);
                } else {
                  // if one neighboring node does neither contain set1 nor set2
                  for (Point2D.Double neighbor : neighbors) {
                    T value = pointsToElements.get(neighbor);
                    if (!set1.contains(value) && !set2.contains(value)) {
                      conflictGraph.addEdge(set1ID, set2ID);
                    }
                  }
                }
              }
            }
            set2ID++;
          }
          set1ID++;
        }
      }
    }

    return conflictGraph;

  }

}

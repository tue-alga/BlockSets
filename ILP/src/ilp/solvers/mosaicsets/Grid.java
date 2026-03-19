package ilp.solvers.mosaicsets;

import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.Multigraph;
import org.jgrapht.graph.WeightedMultigraph;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;

/**
 * Class that represents the grid graph that is used to embedd the system.
 */
public class Grid {

  public static final int TYPE_HEX = 1;
  public static final int TYPE_SQUARE = 2;

  public Point2D.Double[][] points;
  public Multigraph<Point2D.Double, DefaultEdge> graph;

  public double colSpacing;
  public double rowSpacing;

  private double y0;
  private double x0;

  private int type;

  public Grid(int rows, int cols, double colSpacing, double x0, double y0,
      int type) {
    points = new Point2D.Double[rows][cols];
    graph = new WeightedMultigraph<Point2D.Double, DefaultEdge>(
        DefaultEdge.class);

    this.x0 = x0;
    this.y0 = y0;
    this.colSpacing = colSpacing;
    this.rowSpacing = colSpacing;
    this.type = type;

    if (type == TYPE_HEX) {
      this.rowSpacing = colSpacing * Math.sin(Math.PI / 3.0);
    }

    // y-coord of first row
    double y = y0;

    // iterate all rows
    for (int i = 0; i < rows; i++) {

      // x-coord of first point in row i
      double x = x0;
      if (type == TYPE_HEX && i % 2 == 1) {
        x += 0.5 * colSpacing;
      }

      // iterate all columns
      for (int j = 0; j < cols; j++) {
        Point2D.Double p = new Point2D.Double(x, y);
        points[i][j] = p;

        // add vertex
        graph.addVertex(p);

        // add edges
        if (type == TYPE_SQUARE) {
          if (i > 0) {
            graph.addEdge(points[i - 1][j], p);
          }
          if (j > 0) {
            graph.addEdge(points[i][j - 1], p);
          }
        } else if (type == TYPE_HEX) {
          if (j > 0) {
            graph.addEdge(points[i][j - 1], p);
          }
          if (i > 0) {
            graph.addEdge(points[i - 1][j], p);
          }
          if (i % 2 == 1) {
            if (i > 0 && j < cols - 1) {
              graph.addEdge(points[i - 1][j + 1], p);
            }
          } else {
            if (i > 0 && j > 0) {
              graph.addEdge(points[i - 1][j - 1], p);
            }
          }
        }
        x += colSpacing;
      }
      y += rowSpacing;
    }
  }

  /**
   * Helper function to get the center of the grid.
   * 
   * @return
   */
  public Point2D.Double getCenter() {
    int n = this.graph.vertexSet().size();
    double xMid = 0.0;
    double yMid = 0.0;
    for (Point2D p : this.graph.vertexSet()) {
      xMid += p.getX();
      yMid += p.getY();
    }
    xMid /= n;
    yMid /= n;
    return new Point2D.Double(xMid, yMid);
  }

  public List<Point2D.Double> getPointsAroundCenter(int number, double radius) {
    List<Point2D.Double> points = new ArrayList<>();
    Point2D.Double center = this.getCenter();
    for (int i = 0; i < number; i++) {
      double pX = center.x + Math.sin(i * 2 * Math.PI / number) * radius;
      double pY = center.y + Math.cos(i * 2 * Math.PI / number) * radius;
      points.add(new Point2D.Double(pX, pY));
    }
    return points;
  }

  /**
   * Function to extend the grid by one vertex in every direction.
   */
  public void extendGrid() {

    int rows = this.points.length + 2;
    int cols = this.points[0].length + 2;

    Point2D.Double[][] newPoints = new Point2D.Double[rows][cols];

    for (int i = 0; i < this.points[0].length; i++) {
      for (int j = 0; j < this.points.length; j++) {
        newPoints[i + 1][j + 1] = this.points[i][j];
      }
    }

    double y = y0 - rowSpacing;
    for (int i = 0; i < rows; i++) {

      // x-coord of first point in row i

      double x = x0 - colSpacing;
      if (type == TYPE_HEX && i % 2 == 0) {
        x += 0.5 * colSpacing;
      }

      // iterate all columns
      for (int j = 0; j < cols; j++) {
        Point2D.Double p = new Point2D.Double(x, y);

        if (newPoints[i][j] == null) {
          newPoints[i][j] = p;

          // add vertex
          graph.addVertex(p);
        } else {
          p = newPoints[i][j];
        }

        // add edges
        if (type == TYPE_SQUARE) {
          if (i > 0) {
            if (!graph.containsEdge(newPoints[i - 1][j], p))
              graph.addEdge(newPoints[i - 1][j], p);
          }
          if (j > 0) {
            if (!graph.containsEdge(newPoints[i][j - 1], p))
              graph.addEdge(newPoints[i][j - 1], p);
          }
        } else if (type == TYPE_HEX) {
          if (j > 0) {
            if (!graph.containsEdge(newPoints[i][j - 1], p))
              graph.addEdge(newPoints[i][j - 1], p);
          }
          if (i > 0) {
            if (!graph.containsEdge(newPoints[i - 1][j], p))
              graph.addEdge(newPoints[i - 1][j], p);
          }
          if (i % 2 == 0) {
            if (i > 0 && j < cols - 1) {
              if (!graph.containsEdge(newPoints[i - 1][j + 1], p))
                graph.addEdge(newPoints[i - 1][j + 1], p);
            }
          } else {
            if (i > 0 && j > 0) {
              if (!graph.containsEdge(newPoints[i - 1][j - 1], p))
                graph.addEdge(newPoints[i - 1][j - 1], p);
            }
          }
        }
        x += colSpacing;
      }
      y += rowSpacing;
    }

    this.points = newPoints;
  }

  public Path2D.Double getRegion(Point2D.Double p) {
    // DefaultEdge e = graph.outgoingEdgesOf(p).iterator().next();
    // Point2D.Double q = graph.getEdgeTarget(e);
    Path2D.Double path = new Path2D.Double();
    double x, y;
    double alpha;
    double d;

    if (type == TYPE_SQUARE) {
      alpha = Math.PI / 4.0;
      d = 0.5 * colSpacing / Math.cos(Math.PI / 4.0);
      for (int i = 0; i < 4; i++) {
        x = p.x + d * Math.cos(alpha);
        y = p.y + d * Math.sin(alpha);
        if (i == 0)
          path.moveTo(x, y);
        else
          path.lineTo(x, y);
        alpha += Math.PI / 2.0;
      }
      path.closePath();
    } else if (type == TYPE_HEX) {

      alpha = Math.PI / 6.0;
      d = 0.5 * colSpacing / Math.cos(Math.PI / 6.0);
      for (int i = 0; i < 6; i++) {
        x = p.x + d * Math.cos(alpha);
        y = p.y + d * Math.sin(alpha);
        if (i == 0)
          path.moveTo(x, y);
        else
          path.lineTo(x, y);
        alpha += Math.PI / 3.0;
      }
      path.closePath();
    }
    return path;

  }

  /**
   * Helper function to get all triangles of a given overlay in the grid graph.
   * A triangle is a 3-clique of the subgraph induced by the overlay.
   * 
   * @param  arcs All edges of the subgraph representing an overlay
   * @return      List of triangles as path2D.
   */
  public ArrayList<Path2D.Double> getTriangles(List<Line2D.Double> arcs) {
    ArrayList<Path2D.Double> triangles = new ArrayList<Path2D.Double>();

    Set<Point2D.Double> nodes = new HashSet<Point2D.Double>();
    for (Line2D.Double arc : arcs) {
      Point2D.Double p = new Point2D.Double(arc.x1, arc.y1);
      nodes.add(p);
      if (!SetEmbedder.RELAX_PROJECT_CONTIGUITY) {
        p = new Point2D.Double(arc.x2, arc.y2);
        nodes.add(p);
      }
    }

    for (Point2D.Double u : nodes) {
      for (Point2D.Double v : Graphs.neighborListOf(graph, u)) {
        for (Point2D.Double w : Graphs.neighborListOf(graph, u)) {
          if (!v.equals(w)) {
            if (graph.containsEdge(v, w) && nodes.contains(v)
                && nodes.contains(w)) {
              Path2D.Double p = new Path2D.Double();
              p.moveTo(u.x, u.y);
              p.lineTo(v.x, v.y);
              p.lineTo(w.x, w.y);
              p.lineTo(u.x, u.y);
              triangles.add(p);
            }
          }
        }
      }
    }

    return triangles;
  }

  /**
   * Helper function that returns polygons of the overlay. The polygons
   * represent the overlay style.
   * 
   * @param  arcs           All edges of the subgraph representing an overlay
   * @param  edgeColorCount Map that counts how many overlays are already using
   *                        edges
   * @param  strokeWidth    Width of the stroke for drawing the boundary stile
   * @param  borderSize     Size of the border between cells of the base map
   * @param  offset         Offset from the position computed by considering the
   *                        number of overlays already using an edge
   * @param  dontCount      Boolean variable to not increase counters of how
   *                        many times an edge was already used by an overlay
   * @return
   */
  public ArrayList<Path2D.Double> getRegionFromArcset(List<Line2D.Double> arcs,
      HashMap<Point2D.Double, HashMap<Point2D.Double, Integer>> edgeColorCount,
      float strokeWidth, float borderSize, float offset, boolean dontCount) {
    // segments of the polygon
    ArrayList<Line2D.Double> segments = new ArrayList<Line2D.Double>();

    // create list of all vertices in the arc set
    Set<Point2D.Double> nodes = new HashSet<Point2D.Double>();
    for (Line2D.Double arc : arcs) {
      Point2D.Double p = new Point2D.Double(arc.x1, arc.y1);
      nodes.add(p);
      if (!SetEmbedder.RELAX_PROJECT_CONTIGUITY) {
        p = new Point2D.Double(arc.x2, arc.y2);
        nodes.add(p);
      }
    }
    // halfway is the distance between two nodes
    // sidelength is the sidelength of a side of the hexagon.

    // iterate over all nodes in the arc set
    for (Point2D.Double u : nodes) {
      // iterate over all edges of a node and process the neighbour.
      for (Point2D.Double v : Graphs.neighborListOf(graph, u)) {

        // create a segment iff u and v are not in the arc set.
        if (!nodes.contains(v)) {
          int counter = edgeColorCount.get(u).get(v);

          double halfway = (this.colSpacing / 2. - borderSize / 2.
              - (strokeWidth / 2. + strokeWidth * (counter)) - offset);
          double sideLength = 0;
          if (type == TYPE_HEX) {
            sideLength = 2 * Math.tan(Math.PI / 6) * halfway * 0.5;
          } else if (type == TYPE_SQUARE) {
            sideLength = 2 * Math.tan(Math.PI / 4) * halfway * 0.6;
          }
          // System.out.println(counter);

          double dx = (v.x - u.x);
          double dy = (v.y - u.y);

          double l = Math.sqrt(dx * dx + dy * dy);

          dx = dx / l;
          dy = dy / l;

          // x and y are the halfway point between two vertices
          double x = u.x + dx * halfway;
          double y = u.y + dy * halfway;

          // perpendicular vector
          double tmp = dx;
          dx = -dy;
          dy = tmp;

          // endpoints of the segment are perpendicular to the halfway point.
          double x1 = x + dx * sideLength / 2;
          double y1 = y + dy * sideLength / 2;
          double x2 = x - dx * sideLength / 2;
          double y2 = y - dy * sideLength / 2;

          // add the segment
          Line2D.Double segment = new Line2D.Double(x1, y1, x2, y2);

          segments.add(segment);
          counter += 1;

          if (!dontCount) {
            edgeColorCount.get(u).put(v, counter);
            // edgeColorCount.get(v).put(u, counter);
          }
        }
      }
    }

    /*
     * the algorithm to build the polygon picks a random segment first, removes
     * it and uses one of the endpoints as current endpoint. Both endpoints are
     * added to the polygon. Then, it iterates over all remaining segments and
     * finds the segment with the closest endpoint to the current endpoint. Both
     * endpoints are added to the polygon (first the closest) and the one
     * further away is the new current endpoint of the polygon. The segment is
     * removed from the remaining segments. This is repeated until all segments
     * are processed.
     */
    ArrayList<Path2D.Double> paths = new ArrayList<>();
    while (segments.size() > 0) {
      paths.add(getPolygonFromSegs(segments, borderSize));
    }

    return paths;
    // return new Polygon(xpoints, ypoints, xPoints.size());
  }

  /**
   * Compute the polygons from straight line segments representing the boundary
   * of a set.
   * 
   * @param  segments   List of segments
   * @param  borderSize Size of the border between cells in the basemap
   * @return
   */
  public Path2D.Double getPolygonFromSegs(ArrayList<Line2D.Double> segments,
      float borderSize) {
    Path2D.Double path = new Path2D.Double();

    // pick a random segment as start
    double x = segments.get(0).x1;
    double y = segments.get(0).y1;

    path.moveTo(x, y);

    x = segments.get(0).x2;
    y = segments.get(0).y2;

    path.lineTo(x, y);

    Line2D.Double lastSegment = segments.remove(0);
    Line2D.Double firstSegment = lastSegment;

    while (segments.size() > 0) {

      double closestDistance = Float.MAX_VALUE;
      int closestSide = -1;
      int closestIndex = -1;

      // iterate over all remaining segments
      for (int i = 0; i < segments.size(); i++) {

        Line2D.Double segment = segments.get(i);

        // pick the point with the closest distance to the previous point
        double dist1 = Math
            .sqrt(Math.pow(x - segment.x1, 2) + Math.pow(y - segment.y1, 2));
        double dist2 = Math
            .sqrt(Math.pow(x - segment.x2, 2) + Math.pow(y - segment.y2, 2));

        if (dist1 < closestDistance) {
          closestDistance = dist1;
          closestIndex = i;
          closestSide = 1;
        }

        if (dist2 < closestDistance) {
          closestDistance = dist2;
          closestIndex = i;
          closestSide = 2;
        }
      }

      if (closestDistance > rowSpacing || closestDistance > colSpacing) {
        break;
      }

      // remove the closest distance and add the endpoints to the polygon.
      Line2D.Double segment = segments.remove(closestIndex);

      List<Point2D.Double> intersectionPoints = calcIntersectionPoint(
          lastSegment, segment, borderSize);
      // assert intersectionPoints.size() == 1;
      for (Point2D.Double intersection : intersectionPoints) {
        path.lineTo(intersection.x, intersection.y);
      }

      lastSegment = segment;

      if (closestSide == 1) {
        x = segment.x1;
        y = segment.y1;
      } else {
        x = segment.x2;
        y = segment.y2;
      }

      if (closestSide == 1) {
        x = segment.x2;
        y = segment.y2;
      } else {
        x = segment.x1;
        y = segment.y1;
      }
    }

    List<Point2D.Double> intersectionPoints = calcIntersectionPoint(lastSegment,
        firstSegment, borderSize);

    for (Point2D.Double intersection : intersectionPoints) {
      path.lineTo(intersection.x, intersection.y);
    }

    path.closePath();

    return path;
  }

  /**
   * Helper to compute the intersection point between two segments
   * 
   * @param  segment1   First segment
   * @param  segment2   Second segment
   * @param  borderSize Size of the border between two cells on the basemap
   * @return
   */
  private List<Point2D.Double> calcIntersectionPoint(Line2D.Double segment1,
      Line2D.Double segment2, float borderSize) {

    List<Point2D.Double> points = new ArrayList<Point2D.Double>();

    double a1 = segment1.y2 - segment1.y1;
    double b1 = segment1.x1 - segment1.x2;
    double c1 = a1 * segment1.x1 + b1 * segment1.y1;

    double a2 = segment2.y2 - segment2.y1;
    double b2 = segment2.x1 - segment2.x2;
    double c2 = a2 * segment2.x1 + b2 * segment2.y1;

    double det = a1 * b2 - a2 * b1;

    double xx = 0;
    double yy = 0;

    if (Math.abs(det) > 0.0000001) {
      xx = (b2 * c1 - b1 * c2) / det;
      yy = (a1 * c2 - a2 * c1) / det;
      points.add(new Point2D.Double(xx, yy));
    } else {
      if (type == TYPE_SQUARE) {
        if (Math.abs(segment1.x1 - segment2.x2) < Math
            .abs(segment1.y1 - segment2.y2)) {
          double cy1 = (segment1.y1 + segment1.y2) / 2;
          double cy2 = (segment2.y1 + segment2.y2) / 2;
          double cy = (cy1 - cy2) / this.rowSpacing;

          xx = segment1.x1;
          yy = cy1 - cy * (this.colSpacing / 2. - borderSize / 2.);

          points.add(new Point2D.Double(xx, yy));

          xx = (segment1.x1 + segment1.x2 + segment2.x1 + segment2.x2) / 4;
          yy = (segment1.y1 + segment1.y2 + segment2.y1 + segment2.y2) / 4;

          points.add(new Point2D.Double(xx, yy));

          xx = segment2.x1;
          yy = cy2 + cy * (this.colSpacing / 2. - borderSize / 2.);

          points.add(new Point2D.Double(xx, yy));
        } else {
          double cx1 = (segment1.x1 + segment1.x2) / 2;
          double cx2 = (segment2.x1 + segment2.x2) / 2;
          double cx = (cx1 - cx2) / this.colSpacing;

          yy = segment1.y1;
          xx = cx1 - cx * (this.rowSpacing / 2. - borderSize / 2.);

          points.add(new Point2D.Double(xx, yy));

          xx = (segment1.x1 + segment1.x2 + segment2.x1 + segment2.x2) / 4;
          yy = (segment1.y1 + segment1.y2 + segment2.y1 + segment2.y2) / 4;

          points.add(new Point2D.Double(xx, yy));

          yy = segment2.y1;
          xx = cx2 + cx * (this.colSpacing / 2. - borderSize / 2.);

          points.add(new Point2D.Double(xx, yy));
        }
      } else {
        xx = (segment1.x1 + segment1.x2 + segment2.x1 + segment2.x2) / 4;
        yy = (segment1.y1 + segment1.y2 + segment2.y1 + segment2.y2) / 4;

        points.add(new Point2D.Double(xx, yy));
      }
    }

    return points;
  }

}

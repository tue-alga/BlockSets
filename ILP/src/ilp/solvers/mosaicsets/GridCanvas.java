package ilp.solvers.mosaicsets;

import org.jgrapht.graph.DefaultEdge;

import javax.xml.transform.TransformerException;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D.Double;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;

/**
 * Class that contains methods for rendering MosaicSets
 */
public class GridCanvas<T> extends Canvas {
  private static final long serialVersionUID = 1L;
  public Grid grid;

  private Map<Point2D.Double, T> pointsToElements;
  private Map<Integer, List<Double>> setsToSelectedArcs;

  public Map<Integer, Collection<Path2D.Double>> setsToPolygons = new HashMap<>();

  private List<Set<T>> institutes;
  private List<Point2D.Double> initCenters;
  private List<Point2D.Double> finalCenters;

  private Color[] arcColors;
  private Color[] fillColors;

  private boolean drawGrid;
  private boolean drawKelp;
  private boolean drawKelpFusion;
  private boolean drawOutline;
  private boolean drawCenters;
  private int borderSize;
  private Color borderColor;

  private int[] arcOrder;
  private boolean drawLabels;
  private int maxFontSize;
  private Color fontColor;

  /*
   * Initialize GridCanvas with the basemap grid graph.
   */
  public GridCanvas(Grid grid) {
    this.grid = grid;
    this.pointsToElements = null;
    this.arcColors = null;
    this.fillColors = null;
    this.setsToSelectedArcs = null;
    this.arcOrder = null;
    this.drawGrid = false;
    this.drawOutline = false;
    this.drawKelp = false;
    this.borderColor = Color.gray;
    this.borderSize = 1;
    drawLabels = false;
    maxFontSize = 30;
    fontColor = Color.gray;
  }

  @Override
  public void paint(Graphics g2) {

    Graphics2D g = (Graphics2D) g2;

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    // draw grid
    if (this.drawGrid) {
      g.setColor(Color.BLACK);
      for (Point2D.Double p : grid.graph.vertexSet()) {
        g.fillOval((int) Math.rint(p.x - 4.0), (int) Math.rint(p.y - 4.0), 9,
            9);
      }

      for (DefaultEdge dwe : grid.graph.edgeSet()) {
        Point2D.Double p = grid.graph.getEdgeSource(dwe);
        Point2D.Double q = grid.graph.getEdgeTarget(dwe);
        g.drawLine((int) Math.round(p.x), (int) Math.round(p.y),
            (int) Math.round(q.x), (int) Math.round(q.y));
      }
    }

    g.setFont(g.getFont().deriveFont((float) 20.0));

    // fill basemap polygons with color
    if (institutes != null && pointsToElements != null) {
      int setID = 0;

      for (Collection<T> set : institutes) {
        Color fillColor = fillColors[setID];
        if (!setsToPolygons.containsKey(setID))
          setsToPolygons.put(setID, new HashSet<>());
        if (fillColor != null) {
          for (Entry<Point2D.Double, T> entry : pointsToElements.entrySet()) {
            if (set.contains(entry.getValue())) {
              Point2D.Double p = entry.getKey();
              // Polygon pol = grid.getRegion(p);
              Path2D.Double pol = grid.getRegion(p);
              g.setColor(fillColor);
              g.fill(pol);
              setsToPolygons.get(setID).add(pol);
            }
          }
          setID++;
        }
      }
    }

    // draw cell boundaries
    if (pointsToElements != null) {
      for (Entry<Point2D.Double, T> entry : pointsToElements.entrySet()) {
        Point2D.Double p = entry.getKey();
        Path2D.Double pol = grid.getRegion(p);
        g.setStroke(new BasicStroke(this.borderSize));
        g.setColor(this.borderColor);
        g.draw(pol);
      }
    }

    // Initialize helper variables
    HashMap<Point2D.Double, HashMap<Point2D.Double, Integer>> edgeColorCount = new HashMap<Point2D.Double, HashMap<Point2D.Double, Integer>>();
    HashMap<Point2D.Double, HashMap<Point2D.Double, Integer>> kelpEdgeCount = new HashMap<Point2D.Double, HashMap<Point2D.Double, Integer>>();

    HashMap<Point2D.Double, Integer> kelpCount = new HashMap<Point2D.Double, Integer>();

    int baseArcWidth = 3;

    for (Point2D.Double p1 : grid.graph.vertexSet()) {
      HashMap<Point2D.Double, Integer> map = new HashMap<Point2D.Double, Integer>();
      HashMap<Point2D.Double, Integer> map2 = new HashMap<Point2D.Double, Integer>();
      edgeColorCount.put(p1, map);
      kelpEdgeCount.put(p1, map2);
      kelpCount.put(p1, 0);
      for (Point2D.Double p2 : grid.graph.vertexSet()) {
        edgeColorCount.get(p1).put(p2, 0);
        kelpEdgeCount.get(p1).put(p2, 0);
      }
    }

    List<Integer> setsToArcsList = new ArrayList<Integer>();
    HashMap<Integer, Integer> setsToColorMap = new HashMap<Integer, Integer>();

    int colorIndex = 0;
    for (Integer setID : setsToSelectedArcs.keySet()) {
      if (setID < institutes.size())
        continue;
      setsToArcsList.add(setID);
      setsToColorMap.put(setID, colorIndex);
      colorIndex++;
    }

    Collections.sort(setsToArcsList, new Comparator<Integer>() {

      @Override
      public int compare(Integer o1, Integer o2) {
        return arcOrder[setsToColorMap.get(o1)]
            - arcOrder[setsToColorMap.get(o2)];
      }
    });

    // draw the different styles.
    if (setsToSelectedArcs != null && this.drawOutline) {
      drawBoundary(g, setsToArcsList, setsToColorMap, edgeColorCount);
    }

    if (setsToSelectedArcs != null && this.drawKelp) {
      drawKelp(g, setsToArcsList, setsToColorMap, kelpEdgeCount, kelpCount,
          baseArcWidth);
    }

    if (setsToSelectedArcs != null && this.drawKelpFusion) {
      drawKelpFusion(g, setsToArcsList, setsToColorMap, kelpEdgeCount,
          kelpCount, baseArcWidth);
    }

    // draw cell labels
    g.setFont(new Font("Ubuntu Condensed", Font.PLAIN, 16));
    if (this.drawLabels) {
      if (pointsToElements != null) {
        for (float j = this.maxFontSize; j >= 12; j -= 2) {
          Font currentFont = g.getFont();
          Font newFont = currentFont.deriveFont(j);
          g.setFont(newFont);

          boolean allDrawable = true;
          FontMetrics fm = g.getFontMetrics();
          for (Entry<Point2D.Double, T> entry : pointsToElements.entrySet()) {
            T i = entry.getValue();
            String text = "" + i;
            String[] pieces = text.split("(?<=[\\s-]+)");

            for (String t : pieces) {
              // System.out.println(t);
              if ((grid.colSpacing - 15) < fm.stringWidth(t)) {
                allDrawable = false;
                break;
              }
            }

            if (!allDrawable)
              break;
          }

          if (allDrawable) {
            // System.out.println("fontsize: " + j);
            break;
          }
        }
      }

      if (pointsToElements != null) {
        FontMetrics fm = g.getFontMetrics();
        g.setColor(this.fontColor);

        for (Entry<Point2D.Double, T> entry : pointsToElements.entrySet()) {
          Point2D.Double p = entry.getKey();

          T i = entry.getValue();
          String text = "" + i;
          String[] pieces = text.split("(?<=[\\s-]+)");
          List<String> finalPieces = new ArrayList<String>();

          String curStr = "";
          for (String t : pieces) {
            if ((grid.colSpacing - 25) < fm.stringWidth(curStr + t.trim())) {
              finalPieces.add(curStr);
              curStr = t;
            } else {
              curStr = curStr + t;
            }
          }

          finalPieces.add(curStr);

          finalPieces.removeIf(s -> s.equals(""));

          int len = finalPieces.size();
          double ascent = 0.0;

          if (len == 1)
            ascent = 0.0;
          else if (len % 2 == 0)
            ascent = fm.getAscent() * (len / 2.0 - 0.5);
          else
            ascent = fm.getAscent() * ((len - 1) / 2.0);

          double y0 = p.y - ascent + fm.getAscent() / 2;

          if (this.drawKelp)
            y0 += grid.rowSpacing / 4;

          for (String t : finalPieces) {
            g.drawString(t.trim(),
                (int) (Math.rint(p.x - fm.stringWidth(t.trim()) / 2.0)),
                (int) Math.rint(y0));

            y0 = y0 + fm.getAscent();
          }
        }
      }
    }

    // Draw the centers of the sets
    if (drawCenters) {
      g.setStroke(new BasicStroke(3));
      if (finalCenters != null && !finalCenters.isEmpty()) {
        g.setStroke(new BasicStroke(3));
        for (int i = 0; i < finalCenters.size(); i++) {
          Color color = null;
          if (i < fillColors.length)
            color = fillColors[i];
          else
            color = arcColors[i - fillColors.length];
          Point2D.Double center = finalCenters.get(i);
          g.setColor(color);
          g.fillOval((int) center.getX(), (int) center.getY(), 25, 25);
          g.setColor(Color.BLACK);
          g.drawOval((int) center.getX(), (int) center.getY(), 25, 25);
        }
      } else {
        for (int i = 0; i < initCenters.size(); i++) {
          Color color = null;
          if (i < fillColors.length)
            color = fillColors[i];
          else
            color = arcColors[i - fillColors.length];
          Point2D.Double center = initCenters.get(i);
          g.setColor(color);
          g.fillOval((int) center.getX(), (int) center.getY(), 25, 25);
          g.setColor(Color.BLACK);
          g.drawOval((int) center.getX(), (int) center.getY(), 25, 25);
        }
      }
    }

    System.out.println("Done Drawing.");
  }

  /**
   * Function to export a MosaicSet drawing as an SVG.
   * 
   * @param path filepath of the output
   */
//  public void export(String path) {
//    SVGExport exp = new SVGExport();
//    Graphics2D graphic;
//
//    graphic = exp.createContext();
//    this.paint(graphic);
//    try {
//      exp.writeToFile(path);
//    } catch (TransformerException e) {
//      e.printStackTrace();
//    }
//
//  }

  /**
   * Function to draw the boundary style.
   * 
   * @param g              Graphics2D
   * @param setsToArcsList List of list of arcs of each set
   * @param setsToColorMap Map of sets to colors
   * @param edgeColorCount Map of number of edges used in the grid graph
   */
  private void drawBoundary(Graphics2D g, List<Integer> setsToArcsList,
      HashMap<Integer, Integer> setsToColorMap,
      HashMap<Point2D.Double, HashMap<Point2D.Double, Integer>> edgeColorCount) {
    for (Integer setID : setsToArcsList) {
      // Stroke dashed = new BasicStroke(7, BasicStroke.CAP_BUTT,
      // BasicStroke.JOIN_BEVEL, 0, new float[]{9}, colorIndex *
      // 30);
      int colorIndex = setsToColorMap.get(setID);

      float strokeWidth = 8;
      Stroke stroke = new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND,
          BasicStroke.JOIN_ROUND);
      // g.setStroke(dashed);
      g.setStroke(stroke);

      if (arcColors[colorIndex] != null) {

        // System.out.println(setID + " " + arcColors[colorIndex]);

        g.setColor(arcColors[colorIndex]);

        ArrayList<Path2D.Double> polys = grid.getRegionFromArcset(
            setsToSelectedArcs.get(setID), edgeColorCount, strokeWidth,
            this.borderSize, 0, true);
        if (!this.setsToPolygons.containsKey(setID))
          this.setsToPolygons.put(setID, polys);

        g.draw(getAreaFromPolys(polys));

        float strokeWidth2 = 1.5f;
        stroke = new BasicStroke(strokeWidth2, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND);
        g.setStroke(stroke);
        // g.setStroke(dashed);

        // Color brighter = arcColors[colorIndex].brighter();

        // double greyScale = 0.399 * brighter.getRed() + 0.437 *
        // brighter.getGreen() +
        // 0.164 * brighter.getBlue();
        // int alpha = (255 - ((int)greyScale)) * 3 / 5 + 40;
        Color brighter = Color.WHITE;
        Color c = new Color(brighter.getRed(), brighter.getGreen(),
            brighter.getBlue(), 20);
        g.setColor(c);

        polys = grid.getRegionFromArcset(setsToSelectedArcs.get(setID),
            edgeColorCount, strokeWidth, this.borderSize, 1.25f, true);
        g.draw(getAreaFromPolys(polys));

        c = new Color(brighter.getRed(), brighter.getGreen(),
            brighter.getBlue(), 40);

        // g.setColor(arcColors[colorIndex].brighter());
        g.setColor(c);

        polys = grid.getRegionFromArcset(setsToSelectedArcs.get(setID),
            edgeColorCount, strokeWidth, this.borderSize, -0.25f, true);
        g.draw(getAreaFromPolys(polys));

        c = new Color(brighter.getRed(), brighter.getGreen(),
            brighter.getBlue(), 80);

        // g.setColor(arcColors[colorIndex].brighter());
        g.setColor(c);

        polys = grid.getRegionFromArcset(setsToSelectedArcs.get(setID),
            edgeColorCount, strokeWidth, this.borderSize, -1.75f, true);
        g.draw(getAreaFromPolys(polys));

        // stroke = new BasicStroke(strokeWidth2, BasicStroke.CAP_ROUND,
        // BasicStroke.JOIN_ROUND);
        // g.setStroke(dashed);

        // brighter = brighter.brighter();
        // greyScale = 0.299 * brighter.getRed() + 0.587 * brighter.getGreen() +
        // 0.114 *
        // brighter.getBlue();
        // alpha = (255 - ((int)greyScale)) * 2 / 5 + 100;
        c = new Color(brighter.getRed(), brighter.getGreen(),
            brighter.getBlue(), 120);
        // g.setColor(arcColors[colorIndex].brighter());
        g.setColor(c);

        polys = grid.getRegionFromArcset(setsToSelectedArcs.get(setID),
            edgeColorCount, strokeWidth, this.borderSize, -3.25f, false);
        g.draw(getAreaFromPolys(polys));

      }
    }
  }

  /**
   * Function to draw the kelp style
   * 
   * @param g              Graphics2D
   * @param setsToArcsList List of list of arcs of each set
   * @param setsToColorMap Map of sets to colors
   * @param kelpEdgeCount  Map of number of sets coloring a edge in the grid
   *                       graph
   * @param kelpCount      Map of number of sets coloring a vertex in the grid
   *                       graph
   * @param baseArcWidth   Base drawing width of an edge
   */
  private void drawKelp(Graphics2D g, List<Integer> setsToArcsList,
      HashMap<Integer, Integer> setsToColorMap,
      HashMap<Point2D.Double, HashMap<Point2D.Double, Integer>> kelpEdgeCount,
      HashMap<Point2D.Double, Integer> kelpCount,
      int baseArcWidth) {
    Map<Integer, Area> setToKelp = new HashMap<Integer, Area>();

    for (Integer setID : setsToArcsList) {
      int colorIndex = setsToColorMap.get(setID);

      if (arcColors[colorIndex] != null) {
        // System.out.println(setID + " " + arcColors[colorIndex]);

        Set<Point2D.Double> drawnPoints = new HashSet<Point2D.Double>();
        Set<Double> drawnArcs = new HashSet<Double>();
        Area kelp = new Area();
        for (Double arc : setsToSelectedArcs.get(setID)) {
          Point2D.Double p1 = new Point2D.Double(arc.x1, arc.y1);
          Point2D.Double p2 = new Point2D.Double(arc.x2, arc.y2);
          boolean safeToDraw = true;
          for (Double arc2 : drawnArcs) {
            if (arc2.getP1().equals(p1) && arc2.getP2().equals(p2)) {
              safeToDraw = false;
              break;
            } else if (arc2.getP2().equals(p1) && arc2.getP1().equals(p2)) {
              safeToDraw = false;
              break;
            }
          }

          if (!safeToDraw)
            continue;
          else
            drawnArcs.add(arc);

          int counter = kelpEdgeCount.get(p1).get(p2);
          double arcWidth = (counter + 1) * baseArcWidth;

          double dx = (arc.x2 - arc.x1);
          double dy = (arc.y2 - arc.y1);
          double s = Math.sqrt(dx * dx + dy * dy);
          dx = dx / s;
          dy = dy / s;

          double tmp = dx;
          dx = -dy;
          dy = tmp;

          int[] rectX = new int[4];
          int[] rectY = new int[4];
          rectX[0] = (int) (arc.x1 + arcWidth * dx);
          rectY[0] = (int) (arc.y1 + arcWidth * dy);
          rectX[1] = (int) (arc.x2 + arcWidth * dx);
          rectY[1] = (int) (arc.y2 + arcWidth * dy);
          rectX[2] = (int) (arc.x2 - arcWidth * dx);
          rectY[2] = (int) (arc.y2 - arcWidth * dy);
          rectX[3] = (int) (arc.x1 - arcWidth * dx);
          rectY[3] = (int) (arc.y1 - arcWidth * dy);

          Polygon rectPol = new Polygon(rectX, rectY, 4);
          kelp.add(new Area(rectPol));

          kelpEdgeCount.get(p1).put(p2, counter + 1);
          kelpEdgeCount.get(p2).put(p1, counter + 1);

          if (!drawnPoints.contains(p1)) {
            counter = kelpCount.get(p1);
            arcWidth = (counter + 1) * baseArcWidth;

            Ellipse2D.Double point = new Ellipse2D.Double(arc.x1 - 2 * arcWidth,
                arc.y1 - 2 * arcWidth, 4 * arcWidth, 4 * arcWidth);
            kelp.add(new Area(point));

            kelpCount.put(p1, counter + 1);
            drawnPoints.add(p1);
          }

          if (!drawnPoints.contains(p2)) {
            counter = kelpCount.get(p2);
            arcWidth = (counter + 1) * baseArcWidth;

            Ellipse2D.Double point = new Ellipse2D.Double(arc.x2 - 2 * arcWidth,
                arc.y2 - 2 * arcWidth, 4 * arcWidth, 4 * arcWidth);
            kelp.add(new Area(point));

            kelpCount.put(p2, counter + 1);
            drawnPoints.add(p2);
          }

          // point = new Ellipse2D.Double(arcs.x2 - 2 * arcWidth,arcs.y2 - 2 *
          // arcWidth, 4
          // * arcWidth, 4 * arcWidth);
          // kelp.add(new Area(point));
          // g.drawLine(x1, y1, x2, y2);
          // g.drawOval(x2 - 2, y2 - 2, 6, 6);
        }

        setToKelp.put(setID, kelp);
        // g.fill(kelp);
      }
    }

    for (int j = setsToArcsList.size() - 1; j >= 0; j--) {
      Integer setId = setsToArcsList.get(j);
      Area kelp = setToKelp.get(setId);

      int colorIndex = setsToColorMap.get(setId);

      Color color = arcColors[colorIndex];
      color = new Color(color.getRed(), color.getGreen(), color.getBlue(), 150);
      g.setColor(color);
      g.fill(kelp);
      g.setStroke(new BasicStroke(0.7f));
      g.setColor(arcColors[colorIndex]);
      g.draw(kelp);
    }
  }

  /**
   * Function to draw the kelp style
   * 
   * @param g              Graphics2D
   * @param setsToArcsList List of list of arcs of each set
   * @param setsToColorMap Map of sets to colors
   * @param kelpEdgeCount  Map of number of sets coloring a edge in the grid
   *                       graph
   * @param kelpCount      Map of number of sets coloring a vertex in the grid
   *                       graph
   * @param baseArcWidth   Base drawing width of an edge
   */
  private void drawKelpFusion(Graphics2D g, List<Integer> setsToArcsList,
      HashMap<Integer, Integer> setsToColorMap,
      HashMap<Point2D.Double, HashMap<Point2D.Double, Integer>> kelpEdgeCount,
      HashMap<Point2D.Double, Integer> kelpCount,
      int baseArcWidth) {
    Map<Integer, Area> setToKelp = new HashMap<Integer, Area>();

    for (Integer setID : setsToArcsList) {
      int colorIndex = setsToColorMap.get(setID);

      if (arcColors[colorIndex] != null) {
        // System.out.println(setID + " " + arcColors[colorIndex]);

        Set<Point2D.Double> drawnPoints = new HashSet<Point2D.Double>();
        Set<Double> drawnArcs = new HashSet<Double>();
        Area kelp = new Area();

        ArrayList<Path2D.Double> triangles = grid
            .getTriangles(setsToSelectedArcs.get(setID));

        for (Path2D.Double p : triangles) {
          kelp.add(new Area(p));
        }

        for (Double arc : setsToSelectedArcs.get(setID)) {
          Point2D.Double p1 = new Point2D.Double(arc.x1, arc.y1);
          Point2D.Double p2 = new Point2D.Double(arc.x2, arc.y2);
          boolean safeToDraw = true;
          for (Double arc2 : drawnArcs) {
            if (arc2.getP1().equals(p1) && arc2.getP2().equals(p2)) {
              safeToDraw = false;
              break;
            } else if (arc2.getP2().equals(p1) && arc2.getP1().equals(p2)) {
              safeToDraw = false;
              break;
            }
          }

          if (!safeToDraw)
            continue;
          else
            drawnArcs.add(arc);

          int counter = kelpEdgeCount.get(p1).get(p2);
          double arcWidth = (counter + 1) * baseArcWidth;

          double dx = (arc.x2 - arc.x1);
          double dy = (arc.y2 - arc.y1);
          double s = Math.sqrt(dx * dx + dy * dy);
          dx = dx / s;
          dy = dy / s;

          double tmp = dx;
          dx = -dy;
          dy = tmp;

          int[] rectX = new int[4];
          int[] rectY = new int[4];
          rectX[0] = (int) (arc.x1 + arcWidth * dx);
          rectY[0] = (int) (arc.y1 + arcWidth * dy);
          rectX[1] = (int) (arc.x2 + arcWidth * dx);
          rectY[1] = (int) (arc.y2 + arcWidth * dy);
          rectX[2] = (int) (arc.x2 - arcWidth * dx);
          rectY[2] = (int) (arc.y2 - arcWidth * dy);
          rectX[3] = (int) (arc.x1 - arcWidth * dx);
          rectY[3] = (int) (arc.y1 - arcWidth * dy);

          Polygon rectPol = new Polygon(rectX, rectY, 4);
          kelp.add(new Area(rectPol));

          kelpEdgeCount.get(p1).put(p2, counter + 1);
          kelpEdgeCount.get(p2).put(p1, counter + 1);

          if (!drawnPoints.contains(p1)) {
            counter = kelpCount.get(p1);
            arcWidth = (counter + 1) * baseArcWidth;

            Ellipse2D.Double point = new Ellipse2D.Double(arc.x1 - 4 * arcWidth,
                arc.y1 - 4 * arcWidth, 8 * arcWidth, 8 * arcWidth);
            kelp.add(new Area(point));

            kelpCount.put(p1, counter + 1);
            drawnPoints.add(p1);
          }

          if (!drawnPoints.contains(p2)) {
            counter = kelpCount.get(p2);
            arcWidth = (counter + 1) * baseArcWidth;

            Ellipse2D.Double point = new Ellipse2D.Double(arc.x2 - 4 * arcWidth,
                arc.y2 - 4 * arcWidth, 8 * arcWidth, 8 * arcWidth);
            kelp.add(new Area(point));

            kelpCount.put(p2, counter + 1);
            drawnPoints.add(p2);
          }
        }

        setToKelp.put(setID, kelp);
        // g.fill(kelp);
      }
    }

    for (int j = setsToArcsList.size() - 1; j >= 0; j--) {
      Integer setId = setsToArcsList.get(j);
      Area kelp = setToKelp.get(setId);

      int colorIndex = setsToColorMap.get(setId);

      Color color = arcColors[colorIndex];
      color = new Color(color.getRed(), color.getGreen(), color.getBlue(), 150);
      g.setColor(color);
      g.fill(kelp);
      g.setStroke(new BasicStroke(0.7f));
      g.setColor(arcColors[colorIndex]);
      g.draw(kelp);
    }
  }

  /**
   * Helper function to construct an area from different polygons.
   * 
   * @param  polys List of polygons
   * @return       exclusive or computed area
   */
  public Area getAreaFromPolys(ArrayList<Path2D.Double> polys) {

    Area area = new Area();
    for (Path2D.Double p : polys) {
      area.exclusiveOr(new Area(p));
    }

    return area;

  }

  /**
   * Helper function to set all rendering parameters at once.
   * 
   * @param basemap            List of sets of elements in the basemap
   * @param solution           Final solution
   * @param setsToSelectedArcs Map of set IDs to list of arcs
   * @param initialCenters     Initial centers of sets
   * @param finalCenters       Final centers of sets
   * @param fillColors         Colors of the base map
   * @param arcColors          Colors of the overlay sets
   * @param drawGrid           Boolean if the base map grid should be drawn
   * @param drawOutline        Boolean if the boundary style should be used
   * @param drawKelp           Boolean if the Kelp style should be used
   * @param arcOrder           Order in which arcs should be drawn
   * @param borderColor        Color of the border between base map cells
   * @param borderSize         Size of the border between base map cells
   * @param drawLabels         Boolean if labels should be drawn
   * @param maxFontSize        Maximum Font size
   * @param fontColor          Color of the font
   * @param drawCenters        Boolean if centers should be drawn
   * @param drawKelpFusion     Boolean if KelpFusion style should be used
   */
  public void addSolution(List<Set<T>> basemap, Map<Point2D.Double, T> solution,
      Map<Integer, List<Double>> setsToSelectedArcs,
      List<Point2D.Double> initialCenters, List<Point2D.Double> finalCenters,
      Color[] fillColors, Color[] arcColors, boolean drawGrid,
      boolean drawOutline, boolean drawKelp, int[] arcOrder, Color borderColor,
      int borderSize, boolean drawLabels, int maxFontSize, Color fontColor,
      boolean drawCenters, boolean drawKelpFusion) {
    this.institutes = basemap;
    this.pointsToElements = solution;
    this.setsToSelectedArcs = setsToSelectedArcs;
    this.initCenters = initialCenters;
    this.finalCenters = finalCenters;
    this.arcColors = arcColors;
    this.fillColors = fillColors;
    this.arcOrder = arcOrder;
    this.drawGrid = drawGrid;
    this.drawOutline = drawOutline;
    this.drawKelp = drawKelp;
    this.borderColor = borderColor;
    this.borderSize = borderSize;
    this.drawLabels = drawLabels;
    this.maxFontSize = maxFontSize;
    this.fontColor = fontColor;
    this.drawCenters = drawCenters;
    this.drawKelpFusion = drawKelpFusion;
  }

}

package io;

import model.PolygonSolution;
import model.RectangleSolution;
import model.Solution;

import java.awt.Point;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class SolutionWriter {

    private final Solution solution;

    public SolutionWriter(Solution solution) {
        this.solution = solution;
    }

    public static void saveRectangleSolutionToFile(RectangleSolution s, String filename) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write("type: rectangles");

            writer.write("w: " + s.getW() + "\n");
            writer.write("h: " + s.getH() + "\n");

            extractRectangleEntities(s, writer);

            int j = 0;
            for (Integer statement : s.getInstance().statements.keySet()) {
                writer.write("Statement " + s.getInstance().statements.get(statement) + ": (" +
                        s.statementCoordinates[j][0] + ", " +
                        s.statementCoordinates[j][1] + ")\n");
                j++;
            }

            System.out.println("Solution saved to " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void extractRectangleEntities(RectangleSolution s, BufferedWriter writer) throws IOException {
        int i = 0;
        for (Integer entity : s.getInstance().entities.keySet()) {
            writer.write("Entity " + s.getInstance().entities.get(entity) + ": (" +
                    s.entityCoordinates[i][0] + ", " +
                    s.entityCoordinates[i][1] + ") - (" +
                    s.entityCoordinates[i][2] + ", " +
                    s.entityCoordinates[i][3] + ")\n");
            i++;
        }
    }

    public static void savePolygonSolutionToFile(PolygonSolution s, String filename) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write("type: polygons");

            writer.write("w: " + s.getW() + "\n");
            writer.write("h: " + s.getH() + "\n");

            extractPolygonEntities(s, writer);

            int j = 0;
            for (Integer statement : s.getInstance().statements.keySet()) {
                writer.write("Statement " + s.getInstance().statements.get(statement) + ": (" +
                        s.statementCoordinates[j][0] + ", " +
                        s.statementCoordinates[j][1] + ")\n");
                j++;
            }

            System.out.println("Solution saved to " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void extractPolygonEntities(PolygonSolution s, BufferedWriter writer) throws IOException {
        int i = 0;
        for (Integer entity : s.getInstance().entities.keySet()) {
            String entityString = "Entity " + s.getInstance().entities.get(entity) + ": ";
            ArrayList<Point> entityCorners = new ArrayList<>();

            for (int j = 0; j < s.entities[i].length; j++) {
                if (s.entities[i][j][0] > 0) {
                    entityCorners.add(new Point(s.entities[i][j][1], s.entities[i][j][0] - 1));
                    entityCorners.add(new Point(s.entities[i][j][2], s.entities[i][j][0] - 1));
                }
            }

            // Combine points into a string
            String corners = entityCorners.stream()
                    .map(p -> "(" + p.x + ", " + p.y + ")")
                    .collect(Collectors.joining(" - "));

            entityString += corners;
            entityString += "\n";

            writer.write(entityString);
            i++;
        }
    }

    // A method that saves the solution in a way that Vesko can read and draw it
    // (P.S. This is does not output the required by the Visualization format.)
    public static void saveSimplePolygonSolutionToFile(PolygonSolution s, String filename) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write("w: " + s.getW() + "\n");
            writer.write("h: " + s.getH() + "\n");

            extractSimplePolygonEntities(s, writer);

            int j = 0;
            for (Integer statement : s.getInstance().statements.keySet()) {
                writer.write("Statement " + s.getInstance().statements.get(statement) + ": (" +
                        s.statementCoordinates[j][0] + ", " +
                        s.statementCoordinates[j][1] + ")\n");
                j++;
            }

            System.out.println("Polygon solution saved to " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void extractSimplePolygonEntities(PolygonSolution s, BufferedWriter writer) throws IOException {
        int nEntities = s.entities.length;
        int nRows = s.entities[0].length; // assumes all entities have same row count

        for (int i = 0; i < nEntities; i++) {
            Integer entityId = s.getEntityIds().get(i);
            writer.write("Entity " + s.getInstance().entities.get(entityId) + ":\n");

            for (int y = 0; y < nRows; y++) {
                if (s.entities[i][y][0] > 0) {
                    int xStart = s.entities[i][y][1];
                    int xEnd = s.entities[i][y][2];
                    writer.write("    Row " + (s.entities[i][y][0] - 1) + ": (" + xStart + ", " + xEnd + ")\n");
                }
            }
        }
    }

    public void saveToFile(String filename) {
        if (this.solution instanceof RectangleSolution s) {
            saveRectangleSolutionToFile(s, filename);
        } else if (this.solution instanceof PolygonSolution s) {
            savePolygonSolutionToFile(s, filename);
        }
    }

    // Optional: static utility method to write multiple solutions together
    public static void saveMultipleToFile(ArrayList<Solution> solutions, int w, int h, String filename) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            if (solutions.get(0) instanceof RectangleSolution) {
                writer.write("type: rectangles\n");
            } else if (solutions.get(0) instanceof PolygonSolution) {
                writer.write("type: polygons\n");
            }
            writer.write("w: " + w + "\n");
            writer.write("h: " + h + "\n");

            for (Solution solution : solutions) {
                if (solution instanceof RectangleSolution s) {
                    extractRectangleEntities(s, writer);
                } else if (solution instanceof PolygonSolution s) {
                    extractPolygonEntities(s, writer);
                }
            }

            for (Solution solution : solutions) {
                if (solution instanceof RectangleSolution s) {
                    int j = 0;
                    for (Integer statement : s.getInstance().statements.keySet()) {
                        writer.write("Statement " + solution.getInstance().statements.get(statement) + ": (" +
                                s.statementCoordinates[j][0] + ", " +
                                s.statementCoordinates[j][1] + ")\n");
                        j++;
                    }
                } else if (solution instanceof PolygonSolution s) {
                    int j = 0;
                    for (Integer statement : s.getInstance().statements.keySet()) {
                        writer.write("Statement " + solution.getInstance().statements.get(statement) + ": (" +
                                s.statementCoordinates[j][0] + ", " +
                                s.statementCoordinates[j][1] + ")\n");
                        j++;
                    }
                }
            }

            System.out.println("Solution saved to " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

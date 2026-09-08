import ilp.PolygonType;
import ilp.solvers.NewSolver;
import ilp.solvers.SolutionPositioner;
import io.SolutionWriter;
import io.StatementEntityReader;
import io.StatsRecorder;
import model.PositionedSolution;
import model.Solution;
import model.StatementEntityInstance;

import javax.swing.text.Position;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Flags {
    public static void main(String[] args) throws IOException {
        for (var polygonType : new PolygonType[]{PolygonType.Rectangle, PolygonType.Gamma, PolygonType.Nabla, PolygonType.Orthoconvex}) {
            var inputFile = new File("example_data/CountryFlags.json");
            var inputName = inputFile.getName().substring(0, inputFile.getName().lastIndexOf('.'));
            var warmFile = new File("example_solutions/CountryFlags_" + polygonType.ordinal() + ".txt"); // except nabla is different
            if (polygonType == PolygonType.Nabla) {
                warmFile = new File("CountryFlags_augmented_3.txt"); // nabla starts from gamma
            }

            StatementEntityInstance instance = StatementEntityReader.readFromFile(inputFile.getPath());

            var statementFile = new FileReader(warmFile.getPath());

            BufferedReader reader = new BufferedReader(statementFile);
            String line;

            Pattern pattern = Pattern.compile("Statement (.+?): \\(([^,]+), ([^)]+)\\)");

            HashMap<Integer, Point> statementPositions = new HashMap<>();

            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);

                if (matcher.matches()) {
                    String label = matcher.group(1);
                    int x = Integer.parseInt(matcher.group(2).trim());
                    int y = Integer.parseInt(matcher.group(3).trim());

                    for (var entry : instance.statements.entrySet()) {
                        if (entry.getValue().equals(label)) {
                            statementPositions.put(entry.getKey(), new Point(x, y));
                        }
                    }
                }
            }
            reader.close();


            String[] runParams = {inputName, "Best of RectEuler and BlockSets", polygonType.name()};
            StatsRecorder stats = new StatsRecorder(instance, runParams);
            Orchestrator orchestrator = new Orchestrator(5, 1.0 / 3, 60 * 60 * 2, 60);

            PositionedSolution finalLayout;
            try {
                var solver = new NewSolver(polygonType);
                List<Solution> sols;
                sols = orchestrator.solveWithSplits(solver, instance, stats, polygonType, statementPositions);
                sols.removeIf(solution -> solution.getEntityIds().isEmpty());

                long beforeArrange = System.nanoTime();
                finalLayout = SolutionPositioner.computeCompleteSolution((ArrayList<Solution>) sols, polygonType, orchestrator.componentArrangementTimeLimit);
                long afterArrange = System.nanoTime();
                double arrangeTimeS = (afterArrange - beforeArrange) / 1_000_000_000.0;
                stats.totalArrangeTime += arrangeTimeS;

                stats.updateShapeStatsFinalLayout(finalLayout);
            } catch (Exception e) {
                e.printStackTrace();
                finalLayout = PositionedSolution.EMPTY;
            }

            // Write solution stats to file
            stats.appendToCsv(new File("stats_flag_augment.csv"));

            // Write result to file
            SolutionWriter.saveMultipleToFile(
                    finalLayout.solutions,
                    finalLayout.width,
                    finalLayout.height,
                    "CountryFlags_augmented_" + polygonType.ordinal() + ".txt");
        }
    }
}

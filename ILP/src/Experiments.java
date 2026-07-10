import ilp.PolygonType;
import io.SolutionWriter;
import io.StatementEntityReader;
import io.StatsRecorder;
import model.PositionedSolution;
import model.StatementEntityInstance;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Experiments {
    public static void main(String[] args) {
        String inputFolder = "experiment_data/";
        String outputFolder = "experiment_solutions/";
        var statsFile = new File("experiment_statistics.csv");

        var inputFolderDir = new File(inputFolder);
        var outputFolderDir = new File(outputFolder);
        assert(inputFolderDir.isDirectory());

        var uniqueID = System.currentTimeMillis();

        File[] files = inputFolderDir.listFiles();
        Arrays.sort(files, Comparator.comparingLong(File::length));

        try {
            for (boolean useRectEulerSplit : new boolean[]{false, true}) {
                for (File file : files) {
                    var inputName = file.getName().split("\\.(?=[^\\.]+$)")[0];
                    for (var polygonType : new PolygonType[]{PolygonType.Arbitrary, PolygonType.Orthoconvex, PolygonType.Nabla, PolygonType.Gamma, PolygonType.Rectangle}) {
                        boolean[] mosaicSetsPerimeterOptions;
                        if (polygonType == PolygonType.Arbitrary) {
                            mosaicSetsPerimeterOptions = new boolean[]{false, true};
                        } else {
                            mosaicSetsPerimeterOptions = new boolean[]{false};
                        }
                        for (var mosaicSetsPerimeter : mosaicSetsPerimeterOptions) {
                            String outputName = inputName + "_" + polygonType.name();
                            if (mosaicSetsPerimeter) {
                                outputName += "_perimeter";
                            }
                            if (useRectEulerSplit) {
                                var statementFile = new FileReader(outputFolder + outputName + ".txt");

                                // We are going to determine whether this was split by the BlockSets algorithm
                                // If so, we know we need to do this too for RectEuler
                                // If not, the solution will be the same
                                boolean wasSplit = false;

                                BufferedReader reader = new BufferedReader(statementFile);
                                String line;

                                Pattern pattern = Pattern.compile("Entity (.+?): ((?:\\(\\d+, \\d+\\)(?: - )?)+)");

                                Set<String> entities = new HashSet<>();


                                while ((line = reader.readLine()) != null) {
                                    Matcher matcher = pattern.matcher(line);

                                    if (matcher.matches()) {
                                        String label = matcher.group(1);
                                        if (entities.contains(label)) {
                                            wasSplit = true;
                                            break;
                                        }
                                        entities.add(label);
                                    }
                                }
                                reader.close();
                                if (!wasSplit) continue;
                                outputName += "_rectEulerSplit";
                            }
                            var outputFile = new File(outputFolder + outputName + ".txt");
                            if (outputFile.exists()) continue;

                            System.out.println("==== " + outputName + " ====");

                            StatementEntityInstance instance = StatementEntityReader.readFromFile(file.getPath());

                            String[] runParams = {inputName, useRectEulerSplit ? "RectEuler" : "BlockSets", polygonType.name()};
                            StatsRecorder stats = new StatsRecorder(instance, runParams);
                            Orchestrator orchestrator = new Orchestrator(5, 1.0 / 3, 60, 60);
                            PositionedSolution finalLayout = orchestrator.runBlockSets(instance, polygonType, stats, useRectEulerSplit, mosaicSetsPerimeter);

                            // Write solution stats to file
                            stats.appendToCsv(statsFile);

                            // Write result to file
                            SolutionWriter.saveMultipleToFile(
                                    finalLayout.solutions,
                                    finalLayout.width,
                                    finalLayout.height,
                                    outputFile.getPath());
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

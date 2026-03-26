import ilp.PolygonType;
import io.SolutionWriter;
import io.StatementEntityReader;
import io.StatsRecorder;
import model.PositionedSolution;
import model.StatementEntityInstance;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Experiments {
    public static void main(String[] args) {
        String inputFolder = "experiment_data/";
        String outputFolder = "experiment_solutions/";
        var statsFile = new File("experiment_statistics.csv");

        var inputFolderDir = new File(inputFolder);
        var outputFolderDir = new File(outputFolder);
        assert(inputFolderDir.isDirectory());

        var uniqueID = System.currentTimeMillis();

        try {
            for (var file : inputFolderDir.listFiles()) {
                var inputName = file.getName().split("\\.(?=[^\\.]+$)")[0];
                for (var polygonType : new PolygonType[]{PolygonType.Arbitrary, PolygonType.Orthoconvex, PolygonType.Nabla, PolygonType.Gamma, PolygonType.Rectangle}) {
                    String outputName = inputName + "_" + polygonType.name();
                    var outputFile = new File(outputFolder + outputName + ".txt");
                    if (outputFile.exists()) continue;

                    System.out.println("==== " + outputName + " ====");

                    StatementEntityInstance instance = StatementEntityReader.readFromFile(file.getPath());

                    String[] runParams = {inputName, "BlockSets", polygonType.name()};
                    StatsRecorder stats = new StatsRecorder(instance, runParams);
                    Orchestrator orchestrator = new Orchestrator(5, 1.0 / 3, 120, 120);
                    PositionedSolution finalLayout = orchestrator.runBlockSets(instance, polygonType, stats);

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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

import ilp.PolygonType;
import io.SolutionWriter;
import io.StatementEntityReader;
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

        var inputFolderDir = new File(inputFolder);
        var outputFolderDir = new File(outputFolder);
        assert(inputFolderDir.isDirectory());

        var uniqueID = System.currentTimeMillis();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("log_" + uniqueID + ".txt"))) {
            for (var file : inputFolderDir.listFiles()) {
                var inputName = file.getName().split("\\.(?=[^\\.]+$)")[0];
                for (var polygonType : new PolygonType[]{PolygonType.Orthoconvex, PolygonType.Nabla, PolygonType.Gamma, PolygonType.Rectangle}) {
                    String outputName = inputName + "_" + polygonType.name();
                    if ((new File(outputFolder + outputName + ".txt")).exists()) continue;

                    System.out.println(outputName);
                    writer.write(outputName + "\n");
                    writer.flush();

                    StatementEntityInstance instance = StatementEntityReader.readFromFile(inputName + ".json");
                    Orchestrator orchestrator = new Orchestrator(5, 1.0 / 3, 60, 60);
                    PositionedSolution finalLayout = orchestrator.runBlockSets(instance, polygonType, writer);

                    // Write result to file
                    SolutionWriter.saveMultipleToFile(
                            finalLayout.solutions,
                            finalLayout.width,
                            finalLayout.height,
                            outputName + ".txt");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

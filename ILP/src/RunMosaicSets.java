import ilp.solvers.MosaicSetsSolver;
import io.SolutionWriter;
import io.StatementEntityReader;
import io.StatsRecorder;
import model.PositionedSolution;
import model.Solution;
import model.StatementEntityInstance;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class RunMosaicSets {
    public static void main(String[] args) {
        String inputFolder = "experiment_data/";
        String outputFolder = "experiment_solutions_MosaicSets/";
        var statsFile = new File("experiment_statistics_MosaicSets.csv");

        var inputFolderDir = new File(inputFolder);
        var outputFolderDir = new File(outputFolder);
        outputFolderDir.mkdir();
        assert(inputFolderDir.isDirectory());

        var uniqueID = System.currentTimeMillis();

        File[] files = inputFolderDir.listFiles();
        Arrays.sort(files, Comparator.comparingLong(File::length));

        for (File file : files) {
            var inputName = file.getName().split("\\.(?=[^\\.]+$)")[0];

            String outputName = inputName + "_MosaicSets";
            var outputFile = new File(outputFolder + outputName + ".txt");
            if (outputFile.exists()) continue;

            try {
                MosaicSetsSolver solver = new MosaicSetsSolver(0, 0, true, false, outputFolder);
                StatementEntityInstance instance = StatementEntityReader.readFromFile(file.getPath());
                int dimension = (int) (Math.ceil(Math.sqrt(instance.numberOfStatements)) + 1);
                long beforeSolve = System.nanoTime();
                var sol = solver.solve(instance, 600, dimension).getFirst();
                long afterSolve = System.nanoTime();
                double layoutTimeS = (afterSolve - beforeSolve) / 1_000_000_000.0;
                String[] runParams = {inputName, "NoSplit", "MosaicSets"};
                StatsRecorder stats = new StatsRecorder(instance, runParams);
                stats.updateShapeStatsSingleComponent(sol);
                stats.totalLayoutTime += layoutTimeS;
                var positionedSol = new PositionedSolution(new ArrayList<Solution>(List.of(new Solution[]{sol})), sol.getW(), sol.getH());
                stats.updateShapeStatsFinalLayout(positionedSol);

                // Write solution stats to file
                stats.appendToCsv(statsFile);

                // Write result to file
                SolutionWriter.saveMultipleToFile(
                        positionedSol.solutions,
                        positionedSol.width,
                        positionedSol.height,
                        outputFile.getPath());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }



    }
}

package split;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.FileWriter;
import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import model.StatementEntityInstance;

// Impelements RectEuler split.
public class ClusterSplit {

    StatementEntityInstance instance;
    int nEntities;
    int nStatements;

    public ClusterSplit(StatementEntityInstance instance) {
        this.instance = instance;
        this.nEntities = instance.numberOfEntities;
        this.nStatements = instance.numberOfStatements;
    }

    public List<StatementEntityInstance> splitWithPython(
            String pythonExePath,
            String scriptPath,
            String workingDir,
            int k,
            int randomState,
            int minClusterSize) {

        try {
            Path inputPath = Paths.get(workingDir, "boolean_vectors.json");
            Path outputPath = Paths.get(workingDir, "split.json");

            long vectorStart = System.nanoTime();
            BooleanVectorData data = buildStatementBooleanVectors(this.instance);
            long vectorEnd = System.nanoTime();
            double vectorBuildTimeSeconds = (vectorEnd - vectorStart) / 1_000_000_000.0;

            saveBooleanVectorsToJson(data, inputPath.toString());

            runPythonClusterSplit(
                    pythonExePath,
                    scriptPath,
                    inputPath.toString(),
                    outputPath.toString(),
                    k,
                    randomState,
                    minClusterSize);

            JSONObject resultJson = readJsonFile(outputPath.toString());
            double pythonClusteringTimeSeconds = resultJson.getDouble("clustering_time_seconds");
            JSONArray assignmentsJson = resultJson.getJSONArray("assignments");

            int[] assignments = new int[assignmentsJson.length()];
            for (int i = 0; i < assignmentsJson.length(); i++) {
                assignments[i] = assignmentsJson.getInt(i);
            }

            long rebuildStart = System.nanoTime();
            List<StatementEntityInstance> clusteredInstances = buildClusteredInstancesFromAssignments(
                    this.instance,
                    data.statementIdsInOrder,
                    assignments,
                    k);
            long rebuildEnd = System.nanoTime();

            double instanceRebuildTimeSeconds = (rebuildEnd - rebuildStart) / 1_000_000_000.0;
            double totalTime = vectorBuildTimeSeconds + pythonClusteringTimeSeconds + instanceRebuildTimeSeconds;
            System.out.println(totalTime);
            return clusteredInstances;

        } catch (Exception e) {
            throw new RuntimeException("Failed to split instance with Python clustering", e);
        }
    }

    public static BooleanVectorData buildStatementBooleanVectors(StatementEntityInstance instance) {
        // Fix a deterministic order for statements and entities
        // so Java and Python interpret rows/columns identically.
        List<Integer> statementIdList = new ArrayList<>(instance.statements.keySet());
        List<Integer> entityIdList = new ArrayList<>(instance.entities.keySet());

        Collections.sort(statementIdList);
        Collections.sort(entityIdList);

        int nStatements = statementIdList.size();
        int nEntities = entityIdList.size();

        int[] statementIdsInOrder = new int[nStatements];
        int[] entityIdsInOrder = new int[nEntities];

        for (int i = 0; i < nStatements; i++) {
            statementIdsInOrder[i] = statementIdList.get(i);
        }
        for (int j = 0; j < nEntities; j++) {
            entityIdsInOrder[j] = entityIdList.get(j);
        }

        // Build lookup: statement ID -> row index
        Map<Integer, Integer> statementIdToRow = new HashMap<>();
        for (int i = 0; i < nStatements; i++) {
            statementIdToRow.put(statementIdsInOrder[i], i);
        }

        // Build lookup: entity ID -> column index
        Map<Integer, Integer> entityIdToCol = new HashMap<>();
        for (int j = 0; j < nEntities; j++) {
            entityIdToCol.put(entityIdsInOrder[j], j);
        }

        // Build matrix[row][col]
        // row = statement
        // col = entity
        // true iff statement belongs to that entity
        boolean[][] vectors = new boolean[nStatements][nEntities];

        for (Map.Entry<Integer, int[]> entry : instance.entityIndToStatements.entrySet()) {
            int entityId = entry.getKey();
            Integer col = entityIdToCol.get(entityId);

            if (col == null) {
                continue;
            }

            int[] statementIds = entry.getValue();
            if (statementIds == null) {
                continue;
            }

            for (int statementId : statementIds) {
                Integer row = statementIdToRow.get(statementId);
                if (row != null) {
                    vectors[row][col] = true;
                }
            }
        }

        return new BooleanVectorData(statementIdsInOrder, entityIdsInOrder, vectors);
    }

    public static void saveBooleanVectorsToJson(BooleanVectorData data, String outputFilePath) {
        JSONObject root = new JSONObject();

        // statement_ids
        JSONArray statementIdsArray = new JSONArray();
        for (int statementId : data.statementIdsInOrder) {
            statementIdsArray.put(statementId);
        }
        root.put("statement_ids", statementIdsArray);

        // entity_ids
        JSONArray entityIdsArray = new JSONArray();
        for (int entityId : data.entityIdsInOrder) {
            entityIdsArray.put(entityId);
        }
        root.put("entity_ids", entityIdsArray);

        // vectors as 0/1 integers
        JSONArray vectorsArray = new JSONArray();
        for (boolean[] row : data.vectors) {
            JSONArray rowArray = new JSONArray();
            for (boolean value : row) {
                rowArray.put(value ? 1 : 0);
            }
            vectorsArray.put(rowArray);
        }
        root.put("vectors", vectorsArray);

        try (FileWriter writer = new FileWriter(outputFilePath)) {
            writer.write(root.toString(4)); // pretty-printed with indentation
        } catch (IOException e) {
            throw new RuntimeException("Failed to write boolean vectors to JSON file: " + outputFilePath, e);
        }
    }

    public static void runPythonClusterSplit(
            String pythonExePath,
            String scriptPath,
            String inputJsonPath,
            String outputJsonPath,
            int k,
            int randomState,
            int minClusterSize) {

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    pythonExePath,
                    scriptPath,
                    inputJsonPath,
                    outputJsonPath,
                    String.valueOf(k),
                    String.valueOf(randomState),
                    String.valueOf(minClusterSize));

            pb.redirectErrorStream(true);

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[python] " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Python cluster_split.py failed with exit code " + exitCode);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to run Python clustering script", e);
        }
    }

    public static List<StatementEntityInstance> buildClusteredInstancesFromAssignments(
            StatementEntityInstance originalInstance,
            int[] statementIdsInOrder,
            int[] assignments,
            int k) {

        List<Set<Integer>> clusterToStatementIds = new ArrayList<>();
        for (int c = 0; c < k; c++) {
            clusterToStatementIds.add(new HashSet<>());
        }

        for (int i = 0; i < statementIdsInOrder.length; i++) {
            int cluster = assignments[i];
            int statementId = statementIdsInOrder[i];
            clusterToStatementIds.get(cluster).add(statementId);
        }

        List<StatementEntityInstance> result = new ArrayList<>();

        for (int c = 0; c < k; c++) {
            Set<Integer> clusterStatementIds = clusterToStatementIds.get(c);

            Map<Integer, String> newStatements = new HashMap<>();
            for (int statementId : clusterStatementIds) {
                newStatements.put(statementId, originalInstance.statements.get(statementId));
            }

            Map<Integer, String> newEntities = new HashMap<>();
            Map<Integer, int[]> newEntityToStatements = new HashMap<>();

            for (Map.Entry<Integer, int[]> entry : originalInstance.entityIndToStatements.entrySet()) {
                int entityId = entry.getKey();
                int[] oldStatementIds = entry.getValue();

                List<Integer> kept = new ArrayList<>();
                if (oldStatementIds != null) {
                    for (int stmtId : oldStatementIds) {
                        if (clusterStatementIds.contains(stmtId)) {
                            kept.add(stmtId);
                        }
                    }
                }

                if (!kept.isEmpty()) {
                    newEntities.put(entityId, originalInstance.entities.get(entityId));

                    int[] keptArray = new int[kept.size()];
                    for (int i = 0; i < kept.size(); i++) {
                        keptArray[i] = kept.get(i);
                    }
                    newEntityToStatements.put(entityId, keptArray);
                }
            }

            result.add(new StatementEntityInstance(newEntities, newStatements, newEntityToStatements));
        }

        return result;
    }

    public static JSONObject readJsonFile(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return new JSONObject(sb.toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON file: " + filePath, e);
        }
    }

    public static class BooleanVectorData {
        public final int[] statementIdsInOrder;
        public final int[] entityIdsInOrder;
        public final boolean[][] vectors;

        public BooleanVectorData(int[] statementIdsInOrder, int[] entityIdsInOrder, boolean[][] vectors) {
            this.statementIdsInOrder = statementIdsInOrder;
            this.entityIdsInOrder = entityIdsInOrder;
            this.vectors = vectors;
        }
    }
}
// io/StatementEntityReader.java
package io;

import model.StatementEntityInstance;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class StatementEntityReader {
    private StatementEntityReader() {}

    public static StatementEntityInstance readFromFile(String jsonFilePath) throws IOException {
        StringBuilder jsonContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(jsonFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
        }

        JSONObject jsonData = new JSONObject(jsonContent.toString());

        // Parse statements
        JSONArray statementArray = jsonData.getJSONArray("statements");
        Map<Integer, String> statements = new HashMap<>();
        for (int i = 0; i < statementArray.length(); i++) {
            JSONObject obj = statementArray.getJSONObject(i);
            int id = obj.getInt("id");
            String text = obj.getString("text");
            statements.put(id, text);
        }

        // Parse entities
        JSONArray entityArray = jsonData.getJSONArray("entities");
        Map<Integer, String> entities = new HashMap<>();
        for (int i = 0; i < entityArray.length(); i++) {
            JSONObject obj = entityArray.getJSONObject(i);
            int id = obj.getInt("id");
            String name = obj.getString("name");
            entities.put(id, name);
        }

        // Parse entity-to-statement mappings
        JSONObject entityStatementsObj = jsonData.getJSONObject("entity_statements");
        Map<Integer, int[]> entityIndToStatements = new HashMap<>();
        for (String key : entityStatementsObj.keySet()) {
            int entityId = Integer.parseInt(key);
            JSONArray arr = entityStatementsObj.getJSONArray(key);
            int[] stmts = new int[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                stmts[i] = arr.getInt(i);
            }
            entityIndToStatements.put(entityId, stmts);
        }

        // Build and return model
        return new StatementEntityInstance(entities, statements, entityIndToStatements);
    }
}
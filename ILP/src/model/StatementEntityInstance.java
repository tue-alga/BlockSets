package model;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class StatementEntityInstance {
    public int numberOfStatements;
    public int numberOfEntities;
    public HashMap<Integer, String> statements;
    public HashMap<Integer, String> entities;

    // map from entity index to statement indices corresponding to the indices in
    // the arrays entities, statements
    public HashMap<Integer, int[]> entityIndToStatements;

    // NEW Constructor to load JSON data from file (keeping the ids from the
    // dataset)
    public StatementEntityInstance(String jsonFilePath) {
        try {
            StringBuilder jsonContent = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(jsonFilePath));
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            reader.close();

            JSONObject jsonData = new JSONObject(jsonContent.toString());

            // Load statements using their IDs
            JSONArray statementArray = jsonData.getJSONArray("statements");
            numberOfStatements = statementArray.length();
            statements = new HashMap<>();

            for (int i = 0; i < statementArray.length(); i++) {
                JSONObject statementObj = statementArray.getJSONObject(i);
                int id = statementObj.getInt("id"); // Get statement ID
                String text = statementObj.getString("text");
                statements.put(id, text); // Store using the actual ID
            }

            // Load entities using their IDs
            JSONArray entityArray = jsonData.getJSONArray("entities");
            numberOfEntities = entityArray.length();
            entities = new HashMap<>();

            for (int i = 0; i < entityArray.length(); i++) {
                JSONObject entityObj = entityArray.getJSONObject(i);
                int id = entityObj.getInt("id"); // Get entity ID
                String name = entityObj.getString("name");
                entities.put(id, name); // Store using the actual ID
            }

            // Load entity to statement mappings
            JSONObject entityStatementsObject = jsonData.getJSONObject("entity_statements");
            entityIndToStatements = new HashMap<>();

            for (String key : entityStatementsObject.keySet()) {
                int entityId = Integer.parseInt(key); // Entity ID from JSON
                JSONArray statementIndicesArray = entityStatementsObject.getJSONArray(key);
                int[] statementIds = new int[statementIndicesArray.length()];

                for (int j = 0; j < statementIndicesArray.length(); j++) {
                    statementIds[j] = statementIndicesArray.getInt(j); // Store statement ID
                }

                entityIndToStatements.put(entityId, statementIds); // Store using the actual entity ID
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public StatementEntityInstance(
            Map<Integer, String> entities,
            Map<Integer, String> statements,
            Map<Integer, int[]> entityIndToStatements) {
        this.entities = (HashMap<Integer, String>) entities;
        this.statements = (HashMap<Integer, String>) statements;
        this.entityIndToStatements = (HashMap<Integer, int[]>) entityIndToStatements;
        this.numberOfEntities = entities.size();
        this.numberOfStatements = statements.size();
    }

    public StatementEntityInstance(int[] entities, int[] statements, HashMap<Integer, int[]> entityStatements,
            StatementEntityInstance inst) {
        numberOfEntities = entities.length;
        numberOfStatements = statements.length;

        // Add entities
        this.entities = new HashMap<>();

        for (int i = 0; i < numberOfEntities; i++) {
            String name = inst.entities.get(entities[i]);
            this.entities.put(entities[i], name);
        }

        // Add statements
        this.statements = new HashMap<>();

        for (int i = 0; i < numberOfStatements; i++) {
            String text = inst.statements.get(statements[i]);
            this.statements.put(statements[i], text);
        }

        // Add entity to statement map
        this.entityIndToStatements = new HashMap<>(entityStatements);
    }

    public void print() {
        System.out.println("Number of Statements: " + numberOfStatements);
        System.out.println("Number of Entities: " + numberOfEntities);

        System.out.println("\nStatements:");
        for (Map.Entry<Integer, String> entry : statements.entrySet()) {
            System.out.println("  [" + entry.getKey() + "] " + entry.getValue());
        }

        System.out.println("\nEntities:");
        for (Map.Entry<Integer, String> entry : entities.entrySet()) {
            System.out.println("  [" + entry.getKey() + "] " + entry.getValue());
        }

        System.out.println("\nEntity -> Statements mapping:");
        for (Map.Entry<Integer, int[]> entry : entityIndToStatements.entrySet()) {
            int entityId = entry.getKey();
            int[] stmtIds = entry.getValue();

            System.out.print("  [" + entityId + "] " + entities.get(entityId) + " -> ");
            if (stmtIds != null && stmtIds.length > 0) {
                System.out.print("[");
                for (int i = 0; i < stmtIds.length; i++) {
                    System.out.print(stmtIds[i]);
                    if (i < stmtIds.length - 1)
                        System.out.print(", ");
                }
                System.out.println("]");
            } else {
                System.out.println("[]");
            }
        }
    }
}

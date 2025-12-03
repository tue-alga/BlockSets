package split;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import model.StatementEntityInstance;

public class GraphHelper {
    StatementEntityInstance instance;
    HashMap<Integer, ArrayList<Integer>> invertedDeleted;
    HashMap<Integer, ArrayList<Integer>> invertedNonDeleted;
    ArrayList<Node> deletedNodes;
    ArrayList<DeletedNodeGroup> groups;

    GraphHelper(StatementEntityInstance instance) {
        this.instance = instance;
        this.invertedDeleted = new HashMap<>();
        this.invertedNonDeleted = new HashMap<>();
        this.deletedNodes = new ArrayList<>();
        this.groups = new ArrayList<>();
    }

    public void setup(ArrayList<Node> deleted) {
        setDeleted(deleted);
        createMaps();
        createGroups();
    }

    public void setDeleted(ArrayList<Node> deleted) {
        this.deletedNodes = deleted;
    }

    public int containsGroup(ArrayList<Integer> entities, ArrayList<DeletedNodeGroup> groups) {
        for (int i = 0; i < groups.size(); i++) {
            if (entities.equals(groups.get(i).entities)) {
                return i;
            }
        }

        return -1;
    }

    private void createMaps() {
        HashMap<Integer, int[]> entityStatementMap = instance.entityIndToStatements;

        // Go through all entities
        for (Integer entity : entityStatementMap.keySet()) {
            // Go through each entity's statements
            for (int i = 0; i < entityStatementMap.get(entity).length; i++) {
                // Get statement ID
                int id = entityStatementMap.get(entity)[i];

                // If the entity has been deleted, add it to the deleted map
                if (deletedNodes.stream().anyMatch(o -> entity == o.id)) {
                    invertedDeleted.computeIfAbsent(id, k -> new ArrayList<>()).add(entity);
                }
                // Otherwise add it to the non-deleted map
                else {
                    invertedNonDeleted.computeIfAbsent(id, k -> new ArrayList<>()).add(entity);
                }
            }
        }
    }

    private void createGroups() {
        // Go through all statements of deleted nodes
        for (Integer statement : invertedDeleted.keySet()) {

            // If the statement is shared between at least 2 deleted nodes
            if (invertedDeleted.get(statement).size() > 1) {
                // Merge groups with the same entities
                if (containsGroup(invertedDeleted.get(statement), groups) == -1) {
                    groups.add(new DeletedNodeGroup(new ArrayList<>(List.of(statement)), invertedDeleted.get(statement)));
                }
                else {
                    int groupIndex = containsGroup(invertedDeleted.get(statement), groups);
                    groups.get(groupIndex).statements.add(statement);
                }
            }
        }

        Collections.sort(groups, (o1, o2) -> (Integer.compare(o2.entities.size(), o1.entities.size())));
    }
}

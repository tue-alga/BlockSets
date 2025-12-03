package split;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;

import model.StatementEntityInstance;

public class SplitIntanceFactory {
    StatementEntityInstance parentInstance;
    IntersectionGraph graph;
    ArrayList<Integer> addedStatements;

    SplitIntanceFactory(StatementEntityInstance instance, IntersectionGraph graph) {
        this.parentInstance = instance;
        this.graph = graph;
        this.addedStatements = new ArrayList<>();
    }

    public ArrayList<StatementEntityInstance> createInstances() {
        ArrayList<StatementEntityInstance> result = new ArrayList<>();

        for (ArrayList<Node> component : graph.components) {
            // Store all entities in this component
            int[] entities = new int[component.size()];

            // Store all statements in this component
            HashSet<Integer> statements = new HashSet<>();

            // Store the entity-statement map for this component
            HashMap<Integer, int[]> entToSt = new HashMap<>();

            for (int i = 0; i < component.size(); i++) {
                // Add each entity
                Node ent = component.get(i);
                entities[i] = ent.id;

                // For non deleted nodes get their statements from the instance
                if (!ent.deleted) {
                    // Add the statements of this entity to the map of this component
                    int[] arr = parentInstance.entityIndToStatements.get(ent.id);
                    int[] uniqueArr = removeDuplicates(arr);
                    entToSt.put(ent.id, uniqueArr);

                    // Convert the statement array to an ArrayList (to be added to the hashset)
                    ArrayList<Integer> entStatements = new ArrayList<>();
                    for (int j = 0; j < uniqueArr.length; j++) {
                        entStatements.add(uniqueArr[j]);
                    }

                    // Add to statement list for this component
                    statements.addAll(entStatements);
                }
                // For deleted nodes add only their shared statements to the instance
                else {
                    ArrayList<Integer> shared = findSharedStatements(ent, component, false);
                    int[] arr = shared.stream().mapToInt(k -> k).toArray();
                    int[] uniqueArr = removeDuplicates(arr);

                    statements.addAll(shared);
                    entToSt.put(ent.id, uniqueArr);
                }
            }

            // Add to global statement list for this class
            addedStatements.addAll(statements);

            // Create statement array
            int[] stArr = statements.stream().mapToInt(Integer::intValue).toArray();
            // Create instance
            StatementEntityInstance inst = new StatementEntityInstance(entities, stArr, entToSt, parentInstance);
            result.add(inst);
        }

        return handleDeletedNodeStatements(result);
    }

    private ArrayList<StatementEntityInstance> handleDeletedNodeStatements(ArrayList<StatementEntityInstance> result) {
        // Add shared statements between deleted nodes
        addSharedStatementsBetweenDeletedNodes(result);

        // Add the unique statements
        addUniqueDeletedNodeStatements(result);

        return result;
    }

    private void addSharedStatementsBetweenDeletedNodes(ArrayList<StatementEntityInstance> result) {
        for (DeletedNodeGroup group : graph.helper.groups) {
            for (Integer statementId : group.statements) {
                if (!addedStatements.contains(statementId)) {
                    
                    addedStatements.add(statementId);

                    StatementEntityInstance smallestInstance = findSmallestSharedInstance(result, group.entities);

                    String text = parentInstance.statements.get(statementId);
                    smallestInstance.statements.put(statementId, text);
                    smallestInstance.numberOfStatements = smallestInstance.statements.keySet().size();

                    for (Integer entity : group.entities) {
                        int[] currentStatements = smallestInstance.entityIndToStatements.get(entity);

                        int[] updatedStatements = Arrays.copyOf(currentStatements, currentStatements.length + 1);
                        updatedStatements[currentStatements.length] = statementId;

                        // Replace the old (shared) array in the instance's statement-entity map with the combined array
                        smallestInstance.entityIndToStatements.put(entity, updatedStatements);
                    }
                }
            }
        }
    }

    // Add the statements unique to some deleted node to the smallest instance
    private void addUniqueDeletedNodeStatements(ArrayList<StatementEntityInstance> result) {
        // Find the unique statements of all deleted nodes
        for (Node node : graph.deletedNodes) {
            findUniqueStatements(node);

            // Add to global statement list for this class
            addedStatements.addAll(node.uniqueStatements);
        }

        // Sort deleted nodes in decreasing order based on number of unique statements
        Collections.sort(graph.deletedNodes, (o1, o2) -> (Integer.compare(o2.uniqueStatements.size(), o1.uniqueStatements.size())));

        // Add statements contained only in deleted nodes to the smallest new instance
        for (Node node : graph.deletedNodes) {
            // Find the current smallest instance
            StatementEntityInstance smallestInstance = findSmallestInstance(result);

            // Create a map of the statements unique to this deleted node
            HashMap<Integer, String> uniqueStatementMap = new HashMap<>();

            // Fill the map
            for (Integer statementId : node.uniqueStatements) {
                String text = parentInstance.statements.get(statementId);
                uniqueStatementMap.put(statementId, text);
            }

            // Add the statements from the map to the smallest instance
            smallestInstance.statements.putAll(uniqueStatementMap);
            smallestInstance.numberOfStatements = smallestInstance.statements.keySet().size();

            // Get arrays for the unique and shared statements of this node
            int[] uniqueArr = node.uniqueStatements.stream().mapToInt(Integer::intValue).toArray();
            int[] sharedArr = smallestInstance.entityIndToStatements.get(node.id);

            // Make a new array in case this node was not already in the smallest instance
            if (sharedArr == null) sharedArr = new int[0];

            // Make a combined array
            int[] combinedArr = Arrays.copyOf(sharedArr, sharedArr.length + uniqueArr.length);

            System.arraycopy(uniqueArr, 0, combinedArr, sharedArr.length, uniqueArr.length);

            // Replace the old (shared) array in the instance's statement-entity map with the combined array
            smallestInstance.entityIndToStatements.put(node.id, combinedArr);
        }
    }

    private static int[] removeDuplicates(int[] arr) {
        LinkedHashSet<Integer> set = new LinkedHashSet<>();

        // Add elements to LinkedHashSet to remove duplicates
        for (int num : arr) {
            set.add(num);
        }

        // Convert the set back to an int array
        int[] uniqueArray = new int[set.size()];
        int i = 0;
        for (int num : set) {
            uniqueArray[i++] = num;
        }

        return uniqueArray;
    }

    private ArrayList<Integer> findSharedStatements(Node node, ArrayList<Node> component, boolean includeDeleted) {
        // Get only statements shared with another entity
        ArrayList<Integer> sharedStatements = new ArrayList<>();

        // Fill the shared statements list
        for (Edge e : node.adj) {
            // Check if the copy in this component should have this edge
            if (includeDeleted && IntersectionGraph.containsId(e.target, component)) {
                sharedStatements.addAll(e.statements);
            }
            else if (!includeDeleted && IntersectionGraph.containsId(e.target, component) 
                                    && !graph.intersectionGraph[graph.getGraphIndexFromId(e.target)].deleted) {
                sharedStatements.addAll(e.statements);
            }
        }

        return sharedStatements;
    }

    private void findUniqueStatements(Node node) {
        // Get all statements of this node
        int[] allStatements = parentInstance.entityIndToStatements.get(node.id);
        // Get only statements shared with another entity
        ArrayList<Integer> sharedStatements = new ArrayList<>();
        for (ArrayList<Node> component : graph.components) {
            sharedStatements.addAll(findSharedStatements(node, component, true));
        }

        // Get all statement which are only in this entity
        for (int i = 0; i < allStatements.length; i++) {
            if (!sharedStatements.contains(allStatements[i])) {
                node.uniqueStatements.add(allStatements[i]);
            }
        }
    }

    private StatementEntityInstance findSmallestInstance(ArrayList<StatementEntityInstance> instances) {
        int minStatements = Integer.MAX_VALUE;
        StatementEntityInstance smallestInstance = null;

        for (StatementEntityInstance inst : instances) {
            if (inst.statements.size() < minStatements) {
                minStatements = inst.statements.size();
                smallestInstance = inst;
            }
        }

        return smallestInstance;
    }

    private StatementEntityInstance findSmallestSharedInstance(ArrayList<StatementEntityInstance> instances, ArrayList<Integer> nodes) {
        ArrayList<StatementEntityInstance> sharedInstances = new ArrayList<>();
        for (StatementEntityInstance inst : instances) {
            if (inst.entities.keySet().containsAll(nodes)) {
                sharedInstances.add(inst);
            }
        }   

        if (sharedInstances.isEmpty()) return null;

        return findSmallestInstance(sharedInstances);
    }
}

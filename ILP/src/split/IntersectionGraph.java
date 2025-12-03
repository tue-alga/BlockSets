package split;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import model.StatementEntityInstance;

class Node {
    ArrayList<Edge> adj = new ArrayList<>();
    int id;
    int comp = -1;
    boolean deleted = false;
    boolean visited = false;
    HashSet<Integer> uniqueStatements = new HashSet<>();

    Node(int id) {
        this.id = id;
    }

    public Node copy() {
        Node copy = new Node(id);

        for (Edge e : adj) {
            Edge eCopy = new Edge(e.target);

            for (Integer statement : e.statements) {
                eCopy.statements.add(statement);
            }

            copy.adj.add(eCopy);
        }

        copy.comp = this.comp;
        copy.deleted = this.deleted;
        copy.visited = this.visited;
        copy.uniqueStatements = this.uniqueStatements; // same for all copies

        return copy;
    }

    public boolean connectedTo(Node other) {
        return this.adj.stream().anyMatch(o -> other.id == o.target);
    }
}

class Edge {
    int target;
    ArrayList<Integer> statements;

    Edge(int target) {
        this.statements = new ArrayList<>();
        this.target = target;
    }
}

public class IntersectionGraph {
    Node[] intersectionGraph;
    ArrayList<ArrayList<Node>> components = new ArrayList<>();
    private int maxComponent = 0;
    ArrayList<Node> deletedNodes = new ArrayList<>();
    ArrayList<Node> deletedNodeCopies = new ArrayList<>();
    GraphHelper helper;

    IntersectionGraph(StatementEntityInstance instance) {
        createGraph(instance);
        helper = new GraphHelper(instance);
    }

    public int getGraphIndexFromId(int id) {
        for (int i = 0; i < intersectionGraph.length; i++) {
            if (intersectionGraph[i].id == id) {
                return i;
            }
        }

        System.out.println("Something went wrong");
        return -1;
    }

    private void initEdge(int entity1, int entity2) {
        boolean edgeExists = false;
        for (Edge e : intersectionGraph[getGraphIndexFromId(entity1)].adj) {
            if (e.target == entity2) {
                edgeExists = true;
            }
        }

        if (!edgeExists) {
            intersectionGraph[getGraphIndexFromId(entity1)].adj.add(new Edge(entity2));
            intersectionGraph[getGraphIndexFromId(entity2)].adj.add(new Edge(entity1));
        }
    }

    private void addStatement(StatementEntityInstance instance, int entity1, int entity2, int stInd) {
        for (Edge e : intersectionGraph[getGraphIndexFromId(entity1)].adj) {
            if (e.target == entity2) {
                e.statements.add(instance.entityIndToStatements.get(entity1)[stInd]);
            }
        }

        for (Edge e : intersectionGraph[getGraphIndexFromId(entity2)].adj) {
            if (e.target == entity1) {
                e.statements.add(instance.entityIndToStatements.get(entity1)[stInd]);
            }
        }
    }

    public void createEdge(StatementEntityInstance instance, int entity1, int entity2) {
        // Go through both statement lists
        for (int k = 0; k < instance.entityIndToStatements.get(entity1).length; k++) {
            for (int l = 0; l < instance.entityIndToStatements.get(entity2).length; l++) {
                // They share some statement
                if (instance.entityIndToStatements.get(entity1)[k] == instance.entityIndToStatements.get(entity2)[l]) {
                    initEdge(entity1, entity2);
                    addStatement(instance, entity1, entity2, k);
                }
            }
        }
    }

    private void createGraph(StatementEntityInstance instance) {
        intersectionGraph = new Node[instance.numberOfEntities];

        int ind = 0;
        for (Integer entityId : instance.entities.keySet()) {
            intersectionGraph[ind] = new Node(entityId);
            ind++;
        }

        // Create edges
        List<Integer> keys = new ArrayList<>(instance.entities.keySet()); // Get all keys as a list

        for (int i = 0; i < keys.size(); i++) {
            for (int j = i + 1; j < keys.size(); j++) {
                int entity1 = keys.get(i);
                int entity2 = keys.get(j);

                createEdge(instance, entity1, entity2);
            }
        }

        /* Initialize components */

        // Restart dfs // just in case
        for (Node node : intersectionGraph) {
            node.visited = false;
        }

        // Find and assign components with dfs
        for (Node node : intersectionGraph) {
            if (!node.visited && !node.deleted) {
                dfs(getGraphIndexFromId(node.id), maxComponent);
                maxComponent++;
            }
        }

        // Create component lists
        for (int i = 0; i < maxComponent; i++) {
            components.add(new ArrayList<>());
        }

        // Add nodes to the corresponding lists
        for (Node node : intersectionGraph) {
            components.get(node.comp).add(node);
        }
    }

    private void deleteNode(int nodeIndex) {
        // Delete node
        deletedNodes.add(intersectionGraph[nodeIndex]);
        intersectionGraph[nodeIndex].deleted = true;
    }

    private void recomputeComponents(int deletedIndex) {
        ArrayList<Node> affectedComponent = new ArrayList<>();

        // Find component of deleted node
        for (ArrayList<Node> comp : components) {
            if (comp.contains(intersectionGraph[deletedIndex])) {
                comp.remove(intersectionGraph[deletedIndex]);
                affectedComponent = comp;
            }
        }

        // Restart dfs
        for (Node node : affectedComponent) {
            node.visited = false;
        }

        int nrNewComponents = 0;

        // Reassign components
        for (Node node : affectedComponent) {
            if (!node.visited && !node.deleted) {
                dfs(getGraphIndexFromId(node.id), maxComponent);
                maxComponent++;
                nrNewComponents++;
            }
        }

        // Create new component lists
        ArrayList<ArrayList<Node>> newComponents = new ArrayList<>();

        for (int i = 0; i < nrNewComponents; i++) {
            newComponents.add(new ArrayList<>());
        }

        // Assign nodes to new components
        for (int i = 0; i < affectedComponent.size(); i++) {
            int currNodeCompIndex = affectedComponent.get(i).comp - (maxComponent - nrNewComponents);
            newComponents.get(currNodeCompIndex).add(affectedComponent.get(i));
        }

        // Remove old component from main component list
        components.remove(affectedComponent);

        // Add new components to main component list
        for (ArrayList<Node> component : newComponents) {
            components.add(component);
        }
    }

    private void dfs(int node, int component) {
        if (intersectionGraph[node].visited)
            return;
        if (intersectionGraph[node].deleted)
            return;

        intersectionGraph[node].visited = true;
        intersectionGraph[node].comp = component;

        for (Edge e : intersectionGraph[node].adj) {
            dfs(getGraphIndexFromId(e.target), component);
        }
    }

    // Split into components by deleting a set of nodes
    public void split(ArrayList<Integer> deletedNodeIndices) {
        for (Integer nodeIndex : deletedNodeIndices) {
            deleteNode(nodeIndex);
            recomputeComponents(nodeIndex);
        }

        // Fix node component field
        for (ArrayList<Node> component : components) {
            for (Node node : component) {
                node.comp = components.indexOf(component);
            }
        }
    }

    // Merge small components until they fit the specified size bound
    public void merge(Double alpha) {
        // If all nodes were deleted, make two empty components and add each node to both components
        if (components.isEmpty()) {
            components.add(new ArrayList<Node>());
            components.add(new ArrayList<Node>());

            for (ArrayList<Node> component : components) {
                for (int i = 0; i < intersectionGraph.length; i++) {
                    component.add(intersectionGraph[i]);
                }
            }

            return;
        }

        final int minAllowed = (int) Math.ceil(alpha * intersectionGraph.length);
        final int maxAllowed = (int) Math.floor((1 - alpha) * intersectionGraph.length);

        // Sort components by size in increasing order
        Collections.sort(components, (o1, o2) -> (Integer.compare(o1.size(), o2.size())));

        // Track current component size interval
        int minCompSize = components.get(0).size();

        // Merge until all components are within the allowed range (except components
        // that are too large even initially)
        while (minCompSize < minAllowed && components.size() > 2) {
            ArrayList<Node> smallest = components.get(0);
            ArrayList<Node> secondSmallest = components.get(1);

            // If merging would create a component that is too large, stop merging
            if (smallest.size() + secondSmallest.size() > maxAllowed)
                break;

            // Add all nodes from the second smallest component to the smallest
            for (Node node : secondSmallest) {
                smallest.add(node);
                // Update component field for all nodes
                node.comp = smallest.get(0).comp;
            }

            // Remove redundant component
            components.remove(secondSmallest);

            // Fix order
            Collections.sort(components, (o1, o2) -> (Integer.compare(o1.size(), o2.size())));
            minCompSize = components.get(0).size();
        }
    }

    // Add deleted nodes to components
    public void addDeletedNodes() {
        addDeletedCopies();
        addCopiesToPreserveDeletedEdges();
        addAllDeletedNodesToAtLeastOneComponent();
        deleteRedundantEdges();
    }

    public static boolean containsId(int id, ArrayList<Node> component) {
        return component.stream().anyMatch(o -> id == o.id);
    }

    private boolean isConnectedToComponent(Node node, ArrayList<Node> component) {
        for (Edge e : node.adj) {
            if (!intersectionGraph[getGraphIndexFromId(e.target)].deleted && containsId(e.target, component))
                return true;
        }

        return false;
    }

    private int findSmallestComponentContainingNode(int id) {
        int minSize = Integer.MAX_VALUE;
        int compInd = -1;
        for (ArrayList<Node> component : components) {
            if (containsId(id, component) && component.size() < minSize) {
                minSize = component.size();
                compInd = components.indexOf(component);
            }
        }

        return compInd;
    }

    // Find the component with the highest number of nodes from the list (in case of
    // a tie pick the smallest one)
    private int findBestComponent(DeletedNodeGroup group) {
        int max = 0;
        int compId = -1;

        for (ArrayList<Node> component : components) {
            int contains = 0;
            for (Integer entity : group.entities) {
                if (containsId(entity, component))
                    contains++;
            }

            if (max <= contains) {
                if (compId == -1 || component.size() < components.get(findComponentIndex(compId)).size()) {
                    max = contains;
                    compId = component.get(0).comp;
                }
            }
        }

        return compId;
    }

    private ArrayList<Node> findSmallestComponent() {
        Collections.sort(components, (o1, o2) -> (Integer.compare(o1.size(), o2.size())));
        return components.get(0);
    }

    private void addAllDeletedNodesToAtLeastOneComponent() {
        for (Node node : deletedNodes) {
            // Find if the node is contained in some component
            if (findSmallestComponentContainingNode(node.id) == -1) {
                // Make a copy and add it to the smallest component
                Node copy = node.copy();
                ArrayList<Node> smallest = findSmallestComponent();

                // Update the component field
                copy.comp = smallest.get(0).comp;

                smallest.add(copy);
            }
        }
    }

    // Add a copy of each deleted node to each component if the node is connected to
    // some non deleted node in that component
    private void addDeletedCopies() {
        for (Node node : deletedNodes) {
            for (ArrayList<Node> component : components) {
                if (isConnectedToComponent(node, component)) {
                    // Make a copy and set its component
                    Node copy = node.copy();
                    copy.comp = component.get(0).comp;

                    // Add the copy to the component and the global deleted copies list
                    component.add(copy);
                    deletedNodeCopies.add(copy);
                }
            }
        }
    }

    private int findComponent(int id) {
        for (int i = 0; i < this.components.size(); i++) {
            if (containsId(id, components.get(i))) {
                return components.get(i).get(0).comp;
            }
        }

        System.out.println("Something went wrong");
        return -1;
    }

    private void fillGroupComponents() {
        for (DeletedNodeGroup group : helper.groups) {
            for (Integer statement : group.statements) {
                if (helper.invertedNonDeleted.containsKey(statement)) {
                    ArrayList<Integer> entities = helper.invertedNonDeleted.get(statement);
                    int comp = findComponent(entities.get(0));
                    group.components.add(comp);
                }
            }
        }
    }

    private int findComponentIndex(int comp) {
        for (int i = 0; i < components.size(); i++) {
            if (components.get(i).get(0).comp == comp) {
                return i;
            }
        }

        System.out.println("Something went wrong");
        return -1;
    }

    private void putCopyInComponent(int id, ArrayList<Node> component) {
        Node node = intersectionGraph[getGraphIndexFromId(id)];

        // Make a copy and update its component
        Node copy = node.copy();
        copy.comp = component.get(0).comp;

        // Add the copy to the component and the global deleted copies list
        component.add(copy);
        deletedNodeCopies.add(copy);
    }

    private void addCopiesToComponent(DeletedNodeGroup group, ArrayList<Node> component) {
        // Track which nodes are not already in the component
        ArrayList<Integer> missingFromComponent = new ArrayList<>();

        // Add all missing nodes
        for (Integer entity : group.entities) {
            if (!containsId(entity, component)) {
                missingFromComponent.add(entity);
            }
        }

        // Copy the missing nodes into the component
        for (Integer missingId : missingFromComponent) {
            putCopyInComponent(missingId, component);
        }
    }

    private void addCopiesToPreserveDeletedEdges() {
        helper.setup(deletedNodes);

        fillGroupComponents();

        for (DeletedNodeGroup group : helper.groups) {
            if (!group.components.isEmpty()) {
                for (Integer component : group.components) {
                    int index = findComponentIndex(component);
                    addCopiesToComponent(group, components.get(index));
                }
            } else {
                int compId = findBestComponent(group);
                group.components.add(compId);
                addCopiesToComponent(group, components.get(findComponentIndex(compId)));
            }
        }
    }

    /*
     * For each edge between deleted nodes where the nodes do not share a component,
     * find the smallest component with a copy of one deleted node and add to it a
     * copy of the other deleted node
     */

    // For all nodes, remove edges to any node that is not in their component
    private void deleteRedundantEdges() {
        for (ArrayList<Node> component : components) {
            for (Node node : component) {
                Node copy = node.copy();

                // Remove all edges to nodes not in this component
                for (Edge e : copy.adj) { // Iterate through the copy adjacency list
                    if (!containsId(e.target, component)) {
                        node.adj.remove(e); // Remove from actual adjacency list
                    }
                }
            }
        }
    }

    public void printGraph() {
        System.out.println("___________________ PRINTING GRAGH ______________________");
        System.out.println("Number of entities: " + this.intersectionGraph.length);
        System.out.println("Number of components: " + this.components.size());
        System.out.println("_______ Components ______");
        for (ArrayList<Node> comp : components) {
            System.out.println();
            for (Node node : comp) {
                System.out.print(node.id + " ");
            }
            System.out.println();
            for (Node node : comp) {
                System.out.print("Node " + node.id + " connects to: ");
                for (Edge e : node.adj) {
                    System.out.print(e.target + " ");
                }
            }
        }
        System.out.println("_____ Deleted nodes _____");
        for (Node node : deletedNodes) {
            System.out.print(node.id + " ");
        }
    }
}

package split;
import java.util.ArrayList;
import java.util.HashSet;

public class DeletedNodeGroup {
    ArrayList<Integer> statements = new ArrayList<>();
    ArrayList<Integer> entities = new ArrayList<>();
    HashSet<Integer> components = new HashSet<>();
    boolean added = false;
    
    DeletedNodeGroup(ArrayList<Integer> statements, ArrayList<Integer> entities) {
        this.statements = statements;
        this.entities = entities;
    }
}

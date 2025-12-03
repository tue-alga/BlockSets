package ilp.variables;

import com.gurobi.gurobi.GRBVar;

public final class VarsPolygons implements Vars{
    // Statement vars: [nStatements][2] => x,y
    public GRBVar[][] statementCoordinates;

    // Statement is on row j
    public GRBVar[][] statementIsOnRow;

    // Entity vars: active rows and coordinates of each row
    public GurobiEntity[] entities;
    
    // Others    
    public GRBVar maxWidth;
    public GRBVar maxHeight;
    public GRBVar diff;
}


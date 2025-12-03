// Solution
let solutionWidth;
let solutionHeight;
let entities = [];
let statements = [];

// Names of copied entities
let copiedEntityNames;
// Colors for copied entities
let copiedEntityColors = [];

// Customize the look of the solution
let VisualizationSettings = {
    entityRender: "stacked", // How to draw entities (stacked or transparent)
    grayscale: document.getElementById("grayscaleToggle").checked, // Whether or not the entity colors should be grayscaled
    headersIncluded: document.getElementById("headerToggle").checked, // Whether or not to draw entity headers
    cornerRadius: document.getElementById('cornerRadius').value, // How much to round entity and statement corners (0 = no rounding)
    enableShadow: document.getElementById("shadowToggle").checked, // Whether or not to draw shadows under entities
    enableOutline: document.getElementById("outlineToggle").checked, // Whether or not to include entity outlines
    outlineWeight: document.getElementById('outlineWeight').value, // Thickness of the outlines
    outlineColor: document.getElementById('outlineColorInput').value, // Color of the outlines
    outlinesUseEntityColor: document.getElementById("outlinesUseEntityColorToggle").checked, // Whether or not to use a darker version of the entity color for outlines
    outlineNonRepeated: document.getElementById("outlineNonRepeatedToggle").checked, // Whether or not to outline non-repeated entities
    outlineRepeated: document.getElementById("outlineRepeatedToggle").checked, // Whether or not to outline repeated entities
    dashRepeated: document.getElementById("dashRepeatedToggle").checked, // Whether or not to use a dashed outline for repeated entities
    textHighlight: "text" // How to highlight entity names in statements (none, text or background)
};


// Grid sizes
const backgroundCellSize = 10;
const cellWidth = 20; // In background cells
let cellHeights = []; // In background cells

// Canvas
const canvas = document.getElementById('canvas');
const c = canvas.getContext('2d');
const font = `normal ${backgroundCellSize}px Cambria`;
const boldFont = `bolder ${backgroundCellSize}px Cambria`;
c.globalCompositeOperation = "source-over";

// Canvas elements to be drawn
let entityRects = [];
let statementCells = [];

// Mapping name to a list of Entity objects with the same name
const groupedMap = new Map();

// The gaps for each row and column
let rowGaps = [];
let columnGaps = [];

// Entity border segments which start or end at each row and column
let rowSegments = [];
let columnSegments = [];

// Tableau20 palette
const colors = [
    "#4E79A7",
    "#E15759",
    "#499894",
    "#F28E2B",
    "#59A14F",
    "#B07AA1",
    "#9D7660",
    "#D37295",
    "#B6992D",
    "#79706E",
    "#8CD17D",
    "#86BCB6",
    "#A0CBE8",    
    "#D4A6C8",
    "#BAB0AC",
    "#D7B5A6",
    "#F1CE63",
    "#FF9D9A",
    "#FFBE7D",
    "#FABFD2"
];

// Color palette to be used for the current visualization
let colorPalette = [];
colors.forEach(c => colorPalette.push(hexToRgb(c)));

// Read solution from input
document.getElementById('fileInput').addEventListener('change', function (event) {
    const file = event.target.files[0];
    if (!file) return;

    reset(); // Clear any previous visuazliations

    const reader = new FileReader();
    reader.onload = function (e) {
        const fileContent = e.target.result;
        parseData(fileContent); // Ensure data is processed first

        setup(); // Compute visualization
        visualize();
    };
    reader.readAsText(file);
});

function parseData(fileContent) {
    // Extract type
    const typeRegex = /type:\s*(\w+)/;
    const typeMatch = fileContent.match(typeRegex);
    let type = null;
    if (typeMatch) {
        type = typeMatch[1].trim();
    }

    // Extract width and height
    const sizeRegex = /w:\s*(\d+)\s*\n\s*h:\s*(\d+)/;
    const sizeMatch = fileContent.match(sizeRegex);

    if (sizeMatch) {
        solutionWidth = parseInt(sizeMatch[1], 10) + 1;
        solutionHeight = parseInt(sizeMatch[2], 10) + 1;
    }

    initializeGapAndEntityArrays(
        solutionWidth,
        solutionHeight,
        rowGaps,
        columnGaps,
        rowSegments,
        columnSegments
    );

    // --- Regex to capture entity lines ---
    // Rectangles: "Entity Name: (x1, y1) - (x2, y2)"
    const rectEntityRegex = /Entity (.+?): \((\-?\d+), (\-?\d+)\) - \((\-?\d+), (\-?\d+)\)/g;

    // Polygons: "Entity Name: (x, y) - (x, y) - (x, y)..."
    const polyEntityRegex = /Entity (.+?): ((?:\(\-?\d+, \-?\d+\)(?:\s*-\s*\(\-?\d+, \-?\d+\))*)+)/g;

    // Statements (same across both types)
    const statementRegex = /Statement (.+?): \((\-?\d+), (\-?\d+)\)/g;

    let match;

    // Extract entities
    if (type === "rectangles") {
        while ((match = rectEntityRegex.exec(fileContent)) !== null) {
            let [_, name, x1, y1, x2, y2] = match;
            x1 = Number(x1);
            y1 = Number(y1);
            x2 = Number(x2);
            y2 = Number(y2);

            const id = entities.length;
            const coords = [];

            for (let y = y1; y <= y2; y++) {
                coords.push(
                    new Point(x1, y), // start of the row
                    new Point(x2, y)  // end of the row
                );
            }
            entities.push({
                id,
                name,
                coords,
                statements: []
            });
        }
    } else if (type === "polygons") {
        while ((match = polyEntityRegex.exec(fileContent)) !== null) {
            let [_, name, coordString] = match;
            const id = entities.length;

            // Parse all (x,y) pairs in the list
            const coordRegex = /\((\-?\d+), (\-?\d+)\)/g;
            let coords = [];
            let cMatch;
            while ((cMatch = coordRegex.exec(coordString)) !== null) {
                coords.push(new Point(Number(cMatch[1]), Number(cMatch[2])));
            }

            entities.push({
                id,
                name,
                coords,
                statements: []
            });
        }
    }

    // Extract statements
    while ((match = statementRegex.exec(fileContent)) !== null) {
        let [_, text, x, y] = match;
        const statementId = statements.length;
        const statement = {
            id: statementId,
            text,
            x: Number(x),
            y: Number(y),
            entities: []
        };
        statements.push(statement);

        // Now associate this statement with entities whose coordinates match
        entities.forEach(entity => {
            if (y >= entity.coords[0].y && y <= entity.coords[entity.coords.length - 1].y) {
                for (let i = 0; i < entity.coords.length - 1; i++) {
                    if (y == entity.coords[i].y && x >= entity.coords[i].x && x <= entity.coords[i + 1].x) {
                        entity.statements.push(statementId); // Add statement ID to the entity's list of statements
                        break;
                    }
                }
            }
        });
    }

    // Find the names of all entities with multiple copies
    copiedEntityNames = getCopiedEntities(entities);
}

// Clear previous visualizations
function reset() {
    // Reset the canvas
    canvas.width = 0;
    canvas.height = 0;
    canvas.style.left = '50%';
    canvas.style.top = '50%';
    canvas.style.transform = 'translate(-50%, -50%)';

    // Get the latest settings
    VisualizationSettings.grayscale = document.getElementById("grayscaleToggle").checked;
    VisualizationSettings.headersIncluded = document.getElementById("headerToggle").checked;
    VisualizationSettings.enableShadow = document.getElementById("shadowToggle").checked;
    VisualizationSettings.enableOutline = document.getElementById("outlineToggle").checked;
    VisualizationSettings.outlinesUseEntityColor = document.getElementById("outlinesUseEntityColorToggle").checked;
    VisualizationSettings.outlineNonRepeated = document.getElementById("outlineNonRepeatedToggle").checked;
    VisualizationSettings.outlineRepeated = document.getElementById("outlineRepeatedToggle").checked;
    VisualizationSettings.dashRepeated = document.getElementById("dashRepeatedToggle").checked;

    // Clear the previous solution
    solutionWidth = undefined;
    solutionHeight = undefined;
    entities = [];
    statements = [];
    copiedEntityNames = undefined;
    copiedEntityColors = undefined;

    // Clear previous elements and dimensions
    entityRects = [];
    statementCells = [];
    cellHeights = [];
    rowGaps = [];
    columnGaps = [];
    rowEntities = [];
    columnEntities = [];

    // Reset the color palette
    colorPalette = [];
    colors.forEach(c => colorPalette.push(hexToRgb(c)));
}

// Prepare and process data
function setup() {
    // localStorage.clear();

    // Initialize the elements to be drawn on screen from the data
    initializeElements(colorPalette, entities, statements, entityRects, statementCells, VisualizationSettings, copiedEntityColors);

    // Prepare entity rectangles to be drawn
    if (VisualizationSettings.entityRender == "transparent") mergeEntityRectsWithSameStatements(entityRects, VisualizationSettings);
    mapEntityRectsToStatements(entityRects, statements);
    processEntityRectHeaders(entityRects, copiedEntityNames, VisualizationSettings);

    if (VisualizationSettings.entityRender == "stacked") {
        // Compute a stacking order of the entities
        computeStacking(entityRects);
    }
    else if (VisualizationSettings.entityRender == "transparent") {
        // Sort entities by size in decreasing order
        entityRects.sort((a, b) => b.cells.size - a.cells.size);
    }

    // Calculate and set pixel dimensions
    calculateGapsAndMargins(entityRects, rowGaps, columnGaps, rowSegments, columnSegments, VisualizationSettings);
    calculateCellHeights(cellHeights, statementCells, solutionHeight);
    positionElements(entityRects, statementCells, rowGaps, columnGaps, rowSegments, columnSegments, VisualizationSettings);
    setCanvasDimensions(rowGaps, columnGaps, cellHeights);

    if (VisualizationSettings.grayscale) {
        assignGrayscaleColors(entityRects);
    }
    // Make the Export button functional
    document.getElementById("export").addEventListener("click", () => exportToSVG(VisualizationSettings));
}

// Clear and redraw solution to show changes
function visualize() {
    c.clearRect(0, 0, canvas.width, canvas.height);

    // drawBackgroundGrid();
    drawElements(entityRects, statementCells, VisualizationSettings);
    
    setTimeout(() => { recomputeAndUpdateCache(colors);}, 1000);
}
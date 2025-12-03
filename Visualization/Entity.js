class Entity {
    constructor(id, name, coords, statements, VisualizationSettings) {
        // Identifiers
        this.id = id;
        this.statements = statements;

        // Cell coordinates
        this.coords = coords;

        this.cells = this.extractCells();
        [this.columnsMap, this.rowsMap] = this.createColumnsAndRowsCellsMap();

        // Cell dimensions
        this.computeDimensions();

        // Intervals for each side
        this.intervals = { top: [], right: [], bottom: [], left: [] };
        this.computeIntervals(VisualizationSettings.headersIncluded);

        // Pixel coordinates
        this.pixelCoords = [];

        // Header names and their colors
        this.headers = [name];
        this.colors = []

        // Visible versions for all headers
        this.displayHeaders = [];

        // SVG paths for whole headers and names within the headers
        this.svgHeaderOutlines = [];
        this.svgNameOutlines = [];

        // Boolean array showing which headers are for copied entities
        this.deleted = [];

        // Boolean specifying if an entity rectangle should be drawn
        this.singleton = false;
    }

    computeDimensions() {
        let tempCoords = this.coords.slice();

        // Sort corner points based on their x coordinate
        tempCoords.sort((a, b) => a.x - b.x);
        // Find the largest difference
        this.width = tempCoords[tempCoords.length - 1].x - tempCoords[0].x;

        // Sort corner points based on their y coordinate
        tempCoords.sort((a, b) => a.y - b.y);
        // Find the largest difference
        this.height = tempCoords[tempCoords.length - 1].y - tempCoords[0].y;
    }

    // Creates a list of Point objects of the cells of the polygon 
    extractCells() {
        // Expand horizontal segments into all filled cells
        let coords = this.coords;
        const filled = new Set();
        for (let i = 0; i < coords.length; i += 2) {
            const start = coords[i];
            const end = coords[i + 1];
            for (let x = start.x; x <= end.x; x++) {
                filled.add(new Point(x, start.y));
            }
        }
        return filled;
    }

    // Create maps:
    // rowsMap: for each row, which cells from that row are part of the polygon
    // columnsMap: for each column, which cells from that column are part of the polygon
    createColumnsAndRowsCellsMap() {
        const columns = new Map();
        for (const cell of this.cells) {
            if (!columns.has(cell.x)) columns.set(cell.x, []);
            columns.get(cell.x).push(cell.y);
        }

        const rows = new Map();
        for (const cell of this.cells) {
            if (!rows.has(cell.y)) rows.set(cell.y, []);
            rows.get(cell.y).push(cell.x);
        }

        // Sort lists of cells in the maps
        columns.forEach(l => l.sort((a, b) => a - b));
        rows.forEach(l => l.sort((a, b) => a - b));

        return [columns, rows];
    }

    computeIntervals(headersIncluded) {
        // Calculate top and bottom intervals
        let topIntervals = [];
        let bottomIntervals = [];

        for (let [rowIdx, rowCells] of this.rowsMap.entries()) {
            let prevRowCells = this.rowsMap.has(rowIdx - 1) ? this.rowsMap.get(rowIdx - 1) : [];
            let nextRowCells = this.rowsMap.has(rowIdx + 1) ? this.rowsMap.get(rowIdx + 1) : [];

            let topXStart = -1;
            let topXEnd = -1;
            let botXStart = -1;
            let botXEnd = -1;

            for (let i = 0; i < rowCells.length; i++) {
                // top interval starts if it hasnt started and the previous row does not have a cell in this column
                if (topXStart == -1 && !prevRowCells.includes(rowCells[i])) {
                    topXStart = rowCells[i];
                }

                // top interval ends if: it started and (we are at the end of the row or the end of a segment of this row, or previous row has a cell in the next column) 
                if (topXStart != -1 && (i == rowCells.length - 1 || rowCells[i + 1] != rowCells[i] + 1 || prevRowCells.includes(rowCells[i + 1]))) {
                    topXEnd = rowCells[i];
                    topIntervals.push(new Interval(topXStart, topXEnd, rowIdx, 'top', this));
                    topXStart = -1;
                    topXEnd = -1;
                }

                // bottom interval starts if it hasnt started and the next row does not have a cell in this column
                if (botXStart == -1 && !nextRowCells.includes(rowCells[i])) {
                    botXStart = rowCells[i];
                }

                // bottom interval ends if: it started and (we are at the end of the row or the end of a segment of this row, or next row has a cell in the next column) 
                if (botXStart != -1 && (i == rowCells.length - 1 || rowCells[i + 1] != rowCells[i] + 1 || nextRowCells.includes(rowCells[i + 1]))) {
                    botXEnd = rowCells[i];
                    bottomIntervals.push(new Interval(botXStart, botXEnd, rowIdx, 'bottom', this));
                    botXStart = -1;
                    botXEnd = -1;
                }
            }
        }

        // Calculate left and right intervals
        let leftIntervals = [];
        let rightIntervals = [];

        for (let [colIdx, colCells] of this.columnsMap.entries()) {
            let prevColCells = this.columnsMap.has(colIdx - 1) ? this.columnsMap.get(colIdx - 1) : [];
            let nextColCells = this.columnsMap.has(colIdx + 1) ? this.columnsMap.get(colIdx + 1) : [];

            let leftYStart = -1;
            let leftYEnd = -1;
            let rightYStart = -1;
            let rightYEnd = -1;

            for (let i = 0; i < colCells.length; i++) {
                // left interval starts if it hasnt started and the previous column does not have a cell in this row
                if (leftYStart == -1 && !prevColCells.includes(colCells[i])) {
                    leftYStart = colCells[i];
                }

                // left interval ends if: it started and (we are at the end of the column or the end of a segment of this column, or previous column has a cell in the next row) 
                if (leftYStart != -1 && (i == colCells.length - 1 || colCells[i + 1] != colCells[i] + 1 || prevColCells.includes(colCells[i + 1]))) {
                    leftYEnd = colCells[i];
                    leftIntervals.push(new Interval(leftYStart, leftYEnd, colIdx, 'left', this));
                    leftYStart = -1;
                    leftYEnd = -1;
                }

                // right interval starts if it hasnt started and the next column does not have a cell in this row
                if (rightYStart == -1 && !nextColCells.includes(colCells[i])) {
                    rightYStart = colCells[i];
                }

                // right interval ends if: it started and (we are at the end of the column or the end of a segment of this column, or next column has a cell in the next row) 
                if (rightYStart != -1 && (i == colCells.length - 1 || colCells[i + 1] != colCells[i] + 1 || nextColCells.includes(colCells[i + 1]))) {
                    rightYEnd = colCells[i];
                    rightIntervals.push(new Interval(rightYStart, rightYEnd, colIdx, 'right', this));
                    rightYStart = -1;
                    rightYEnd = -1;
                }
            }
        }

        // Add calculated top and bottom intervals to the entity
        this.intervals.top = topIntervals;
        this.intervals.bottom = bottomIntervals;
        this.intervals.left = leftIntervals;
        this.intervals.right = rightIntervals;


        // Set the top-left interval if headers are drawn
        this.intervals.top[0].setTopLeft(true, headersIncluded);

        // Sort intervals on each side by their starting coordinate
        this.intervals.top.sort((a, b) => a.start - b.start);
        this.intervals.right.sort((a, b) => a.start - b.start);
        this.intervals.bottom.sort((a, b) => a.start - b.start);
        this.intervals.left.sort((a, b) => a.start - b.start);
    }

    position() {
        // Clear any previously calculated pixel dimensions
        this.pixelCoords = [];

        class Side {
            constructor(start, end) {
                this.start = start; // Start point
                this.end = end;     // End point
            }
        }
        // Store the sides of the polygon
        let sides = [];

        for (let i = 0; i < this.intervals['top'].length; i++) {
            let interval = this.intervals['top'][i];
            // Find the y
            // Sum up the cell heights of every row above this segment
            let combinedPreviousCellHeight = 0;
            for (let j = 0; j < interval.otherCoord; j++) {
                combinedPreviousCellHeight += cellHeights[j];
            }
            let y = backgroundCellSize * combinedPreviousCellHeight;

            // Sum up all row gaps before this row (exclusive)
            let cumulativeRowGap = 0;
            for (let j = 0; j <= interval.otherCoord; j++) {
                cumulativeRowGap += rowGaps[j];
            }
            y += cumulativeRowGap * backgroundCellSize;
            y -= backgroundCellSize * interval.margin;

            interval.setOther(y);
        }

        // Go through right segments
        for (let i = 0; i < this.intervals['right'].length; i++) {
            let interval = this.intervals['right'][i];

            // Find the x
            let x = (interval.otherCoord + 1) * backgroundCellSize * cellWidth;

            // Sum up all column gaps before this column
            let cumulativeColumnGap = 0;
            for (let j = 0; j <= interval.otherCoord; j++) {
                cumulativeColumnGap += columnGaps[j];
            }
            x += cumulativeColumnGap * backgroundCellSize;
            x += backgroundCellSize * interval.margin;

            interval.setOther(x);
        }

        // Go through bottom segments
        for (let i = this.intervals['bottom'].length - 1; i >= 0; i--) {
            let interval = this.intervals['bottom'][i];

            // Find the y
            // Sum up the cell heights of every row above this segment
            let combinedPreviousCellHeight = 0;
            for (let j = 0; j <= interval.otherCoord; j++) {
                combinedPreviousCellHeight += cellHeights[j];
            }
            let y = backgroundCellSize * combinedPreviousCellHeight;

            // Sum up all row gaps before this row (exclusive)
            let cumulativeRowGap = 0;
            for (let j = 0; j <= interval.otherCoord; j++) {
                cumulativeRowGap += rowGaps[j];
            }
            y += cumulativeRowGap * backgroundCellSize;
            y += backgroundCellSize * interval.margin;

            interval.setOther(y);
        }

        // Go through left segments
        for (let i = this.intervals['left'].length - 1; i >= 0; i--) {
            let interval = this.intervals['left'][i];

            // Find the x
            let x = interval.otherCoord * backgroundCellSize * cellWidth;
            // Sum up all column gaps before this column
            let cumulativeColumnGap = 0;
            for (let j = 0; j <= interval.otherCoord; j++) {
                cumulativeColumnGap += columnGaps[j];
            }
            x += cumulativeColumnGap * backgroundCellSize;
            x -= backgroundCellSize * interval.margin;

            interval.setOther(x);
        }

        // Now that all other coordinates are set, find pixel coordinates of the spans
        for (let side in this.intervals) {
            for (let i = 0; i < this.intervals[side].length; i++) {
                let interval = this.intervals[side][i];

                if (interval.side === 'top') {
                    // Find the left or right interval connecting with the start of this top interval
                    let leftInterval = this.intervals['left'].filter(intv => (intv.otherCoord == interval.start && intv.start == interval.otherCoord));
                    let rightInterval = this.intervals['right'].filter(intv => (intv.otherCoord + 1 == interval.start && intv.end + 1 == interval.otherCoord));

                    // Set the start x coordinate to match the left/right interval that exists
                    let xStart = leftInterval[0] != null ? leftInterval[0].otherPixel : rightInterval[0].otherPixel;

                    // Find the left or right interval connecting with the end of this top interval
                    leftInterval = this.intervals['left'].filter(intv => (intv.otherCoord - 1 == interval.end && intv.end + 1 == interval.otherCoord));
                    rightInterval = this.intervals['right'].filter(intv => (intv.otherCoord == interval.end && intv.start == interval.otherCoord));

                    // Set the end x coordinate to match the left/right interval that exists
                    let xEnd = leftInterval[0] != null ? leftInterval[0].otherPixel : rightInterval[0].otherPixel;

                    // Add a new side with the pixel coordinates of this top interval
                    sides.push(new Side(new Point(xStart, interval.otherPixel), new Point(xEnd, interval.otherPixel)));
                    interval.setStart(xStart);
                    interval.setEnd(xEnd);
                }
                else if (interval.side === 'right') {
                    // Find the top or bottom interval connecting with the start of this right interval
                    let topInterval = this.intervals['top'].filter(intv => (intv.otherCoord == interval.start && intv.end == interval.otherCoord));
                    let bottomInterval = this.intervals['bottom'].filter(intv => (intv.otherCoord + 1 == interval.start && intv.start - 1 == interval.otherCoord));

                    // Set the start y coordinate to match the top/bottom interval that exists
                    let yStart = topInterval[0] != null ? topInterval[0].otherPixel : bottomInterval[0].otherPixel;

                    // Find the top or bottom interval connecting with the end of this right interval
                    topInterval = this.intervals['top'].filter(intv => (intv.otherCoord - 1 == interval.end && intv.start - 1 == interval.otherCoord));
                    bottomInterval = this.intervals['bottom'].filter(intv => (intv.otherCoord == interval.end && intv.end == interval.otherCoord));

                    // Set the end y coordinate to match the top/bottom interval that exists
                    let yEnd = topInterval[0] != null ? topInterval[0].otherPixel : bottomInterval[0].otherPixel;

                    // Add a new side with the pixel coordinates of this right interval
                    sides.push(new Side(new Point(interval.otherPixel, yStart), new Point(interval.otherPixel, yEnd)));
                    interval.setStart(yStart);
                    interval.setEnd(yEnd);
                }
                else if (interval.side === 'bottom') {
                    // Find the left or right interval connecting with the start of this bottom interval
                    let leftInterval = this.intervals['left'].filter(intv => (intv.otherCoord == interval.start && intv.end == interval.otherCoord));
                    let rightInterval = this.intervals['right'].filter(intv => (intv.otherCoord + 1 == interval.start && intv.start - 1 == interval.otherCoord));

                    // Set the end x coordinate to match the left/right interval that exists
                    let xStart = leftInterval[0] != null ? leftInterval[0].otherPixel : rightInterval[0].otherPixel;

                    // Find the left or right interval connecting with the end of this bottom interval
                    leftInterval = this.intervals['left'].filter(intv => (intv.otherCoord - 1 == interval.end && intv.start - 1 == interval.otherCoord));
                    rightInterval = this.intervals['right'].filter(intv => (intv.otherCoord == interval.end && intv.end == interval.otherCoord));

                    // Set the start x coordinate to match the left/right interval that exists
                    let xEnd = leftInterval[0] != null ? leftInterval[0].otherPixel : rightInterval[0].otherPixel;

                    // Add a new side with the pixel coordinates of this bottom interval
                    sides.push(new Side(new Point(xEnd, interval.otherPixel), new Point(xStart, interval.otherPixel)));
                    interval.setStart(xStart);
                    interval.setEnd(xEnd);
                }
                else if (interval.side === 'left') {
                    // Find the top or bottom interval connecting with the start of this left interval
                    let topInterval = this.intervals['top'].filter(intv => (intv.otherCoord == interval.start && intv.start == interval.otherCoord));
                    let bottomInterval = this.intervals['bottom'].filter(intv => (intv.otherCoord + 1 == interval.start && intv.end + 1 == interval.otherCoord));

                    // Set the start y coordinate to match the top/bottom interval that exists
                    let yStart = topInterval[0] != null ? topInterval[0].otherPixel : bottomInterval[0].otherPixel;

                    // Find the top or bottom interval connecting with the end of this left interval
                    topInterval = this.intervals['top'].filter(intv => (intv.otherCoord - 1 == interval.end && intv.end + 1 == interval.otherCoord));
                    bottomInterval = this.intervals['bottom'].filter(intv => (intv.otherCoord == interval.end && intv.start == interval.otherCoord));

                    // Set the end y coordinate to match the top/bottom interval that exists
                    let yEnd = topInterval[0] != null ? topInterval[0].otherPixel : bottomInterval[0].otherPixel;

                    // Add a new side with the pixel coordinates of this left interval
                    sides.push(new Side(new Point(interval.otherPixel, yEnd), new Point(interval.otherPixel, yStart)));
                    interval.setStart(yStart);
                    interval.setEnd(yEnd);
                }
            }
        }

        // Store the pixel coordinates of the polygon
        let orderedPoints = [];

        // Find the top-left corner (minimum y, then minimum x)
        let topLeft = sides.reduce((min, side) => {
            let pt = side.start;
            if (
                pt.y < min.y ||
                (pt.y === min.y && pt.x < min.x)
            ) {
                min = pt;
            }
            return min;
        }, sides[0].start);


        // Start from topLeft, build ordered polygon points clockwise
        orderedPoints.push(topLeft);

        // Find the side that starts at topLeft
        let firstSide = sides.find(side => side.start.x === topLeft.x && side.start.y === topLeft.y);
        let currentPoint = firstSide.end;

        orderedPoints.push(new Point(currentPoint.x, currentPoint.y));

        for (let i = 0; i < sides.length; i++) {
            // Find the side that starts at the current point
            let nextSide = sides.find(
                side =>
                    (side.start.x === currentPoint.x && side.start.y === currentPoint.y)
            );
            currentPoint.x = nextSide ? nextSide.end.x : currentPoint.x;
            currentPoint.y = nextSide ? nextSide.end.y : currentPoint.y;

            orderedPoints.push(new Point(currentPoint.x, currentPoint.y));
            if (currentPoint.x === topLeft.x && currentPoint.y === topLeft.y) break;
        }

        this.pixelCoords = orderedPoints;

        // Get the display versions of all headers
        for (let i = 0; i < this.headers.length; i++) {
            this.displayHeaders.push(preprocessEntityName(this.headers[i], this.pixelCoords[1].x - this.pixelCoords[0].x));
        }
    }

    draw(VisualizationSettings) {
        // Only draw non-singleton or copied entities
        if (this.singleton) return;

        // Find the entity's region (and corresponding svg path data)
        let region = roundedPolygonPath(this.pixelCoords, VisualizationSettings.cornerRadius, true)[0];
        this.svgPath = roundedPolygonPath(this.pixelCoords, VisualizationSettings.cornerRadius, true)[1];

        // Add shadow
        if (VisualizationSettings.enableShadow) {
            let shadowRegion = roundedPolygonPath(this.pixelCoords.map(p => new Point(p.x + 3, p.y + 3)), VisualizationSettings.cornerRadius, true)[0];
            this.svgShadowPath = roundedPolygonPath(this.pixelCoords.map(p => new Point(p.x + 3, p.y + 3)), VisualizationSettings.cornerRadius, true)[1];
            c.fillStyle = "rgb(50, 50, 50, 0.5)";
            c.fill(shadowRegion);
        }

        // Draw background
        if (VisualizationSettings.entityRender == "stacked") {
            c.fillStyle = this.colors[this.statements.length > 1 ? 0 : (this.deleted.includes(true) ? this.deleted.indexOf(true) : 0)];
            c.fill(region);
        }
        else if (VisualizationSettings.entityRender == "transparent") {
            for (let i = 0; i < this.colors.length; i++) {
                c.fillStyle = rgbToRgba(this.colors[i], '0.15');
                c.fill(region);
            }

            c.strokeStyle = this.colors[this.statements.length > 1 ? 0 : (this.deleted.includes(true) ? this.deleted.indexOf(true) : 0)];
            c.stroke(region);
        }

        // Draw borders
        if (VisualizationSettings.enableOutline) {
            if (VisualizationSettings.outlinesUseEntityColor) {
                c.strokeStyle = darkenRGB(this.colors[this.statements.length > 1 ? 0 : (this.deleted.includes(true) ? this.deleted.indexOf(true) : 0)], 0.7);
            }
            else {
                c.strokeStyle = rgbToRgba(VisualizationSettings.outlineColor, 0.5);
            }

            if (!this.deleted.includes(true) && VisualizationSettings.outlineNonRepeated) {
                c.lineWidth = VisualizationSettings.outlineWeight;
                c.stroke(region);
            }
            else if (this.deleted.includes(true) && VisualizationSettings.outlineRepeated) {
                if (VisualizationSettings.dashRepeated) {
                    c.setLineDash([6, 2]);
                    c.lineWidth = VisualizationSettings.outlineWeight;
                    c.stroke(region);
                    c.setLineDash([]);
                }
                else {
                    c.lineWidth = VisualizationSettings.outlineWeight;
                    c.stroke(region);
                }
            }
        }
    }

    label(VisualizationSettings) {
        // Only label non-singleton or copied entities
        if (!VisualizationSettings.headersIncluded || this.singleton) return;

        // Track how many headers have been drawn
        let headerIndex = 0;

        // Go through every header
        for (let i = 0; i < this.headers.length; i++) {

            // Only draw headers for non-singleton or copied entities
            if (this.statements.length > 1 || this.deleted[i]) {
                let backgroundColor = this.colors[i];

                let headerCorners = [
                    new Point(this.pixelCoords[0].x + 1, this.pixelCoords[0].y + 2 * headerIndex * backgroundCellSize + 1),
                    new Point(this.pixelCoords[1].x - 1, this.pixelCoords[0].y + 2 * headerIndex * backgroundCellSize + 1),
                    new Point(this.pixelCoords[1].x - 1, this.pixelCoords[0].y + 2 * headerIndex * backgroundCellSize + 1 + 2 * backgroundCellSize),
                    new Point(this.pixelCoords[0].x + 1, this.pixelCoords[0].y + 2 * headerIndex * backgroundCellSize + 1 + 2 * backgroundCellSize)
                ];

                let nameCorners = [
                    new Point(this.pixelCoords[0].x + 1, this.pixelCoords[0].y + 2 * headerIndex * backgroundCellSize + 1),
                    new Point(this.pixelCoords[0].x + 1 + c.measureText(this.displayHeaders[i]).width + 2 * backgroundCellSize, this.pixelCoords[0].y + 2 * headerIndex * backgroundCellSize + 1),
                    new Point(this.pixelCoords[0].x + 1 + c.measureText(this.displayHeaders[i]).width + 2 * backgroundCellSize, this.pixelCoords[0].y + 2 * headerIndex * backgroundCellSize + 1 + 2 * backgroundCellSize),
                    new Point(this.pixelCoords[0].x + 1, this.pixelCoords[0].y + 2 * headerIndex * backgroundCellSize + 1 + 2 * backgroundCellSize)
                ];

                let headerOutline = roundedPolygonPath(headerCorners, Math.max(VisualizationSettings.cornerRadius - 1, 0), false)[0];
                this.svgHeaderOutlines.push(roundedPolygonPath(headerCorners, Math.max(VisualizationSettings.cornerRadius - 1, 0), false)[1]);
                let nameOutline = roundedPolygonPath(nameCorners, Math.max(VisualizationSettings.cornerRadius - 1, 0), false)[0];
                this.svgNameOutlines.push(roundedPolygonPath(nameCorners, Math.max(VisualizationSettings.cornerRadius - 1, 0), false)[1]);

                // For copied entities draw crosshatched headers
                if (this.deleted[i]) {
                    // Draw crosshatching pattern for the rest of the header
                    c.fillStyle = createCrosshatchPattern(backgroundColor);
                    c.fill(headerOutline);

                    // Fill space behind entity name
                    c.fillStyle = backgroundColor;
                    c.fill(nameOutline);

                    // Draw bottom line of crosshatched headers
                    c.fillRect(
                        this.pixelCoords[0].x + Number(VisualizationSettings.cornerRadius),
                        this.pixelCoords[0].y + 2 * headerIndex * backgroundCellSize + 2 * backgroundCellSize - 1,
                        this.pixelCoords[1].x - this.pixelCoords[0].x - Number(VisualizationSettings.cornerRadius) - 1,
                        2
                    );
                }
                // For non-singleton entities draw normal headers
                else {
                    c.fillStyle = backgroundColor;
                    c.fill(headerOutline);
                }

                // Show header name
                c.fillStyle = "#fff";
                c.font = font;
                c.fillText(this.displayHeaders[i], this.pixelCoords[0].x + backgroundCellSize + 1, this.pixelCoords[0].y + 2 * headerIndex * backgroundCellSize + 1.25 * backgroundCellSize + 1);

                // Increase the drawn header counter
                headerIndex++;
            }
        }
    }
}
class Statement {
    constructor(id, x, y, text, entities) {
        // Identifiers
        this.id = id;
        this.text = text;
        this.entities = entities;

        // Cell coordinates
        this.x = x;
        this.y = y;

        // Pixel coordinates
        this.pixelCoords = [];

        // Lines of text stored as separate strings
        this.textLines = splitTextIntoLines(this.text, (cellWidth - 2) * backgroundCellSize);
    }

    // Get all entity names appearing in the statement text and their corresponding colors
    getEntityNamesAndColors() {
        let names = [];
        let colors = [];
        let combined = [];

        // Check the headers of all entities appearing in the statement
        this.entities.forEach(e => {
            for (let i = 0; i < e.headers.length; i++) {
                // Find variations of necessary
                if (e.headers[i].indexOf("(") > -1 || e.headers[i].indexOf("[") > -1) {
                    let variations = extractEntityNames(e.headers[i]);
                    variations.forEach(n => {
                        names.push(n);
                        colors.push(e.colors[i]);
                    });
                }
                else {
                    names.push(e.headers[i]);
                    colors.push(e.colors[i]);
                }
            }
        });

        // Combine names and colors into a single array
        for (let i = 0; i < names.length; i++) {
            combined[i] = [names[i], colors[i]];
        }

        // Sort by name length in increasing order
        combined.sort((a, b) => a[0].length - b[0].length);

        return combined;
    }

    position() {
        // Sum up all row gaps before the statement
        let cumulativeRowGap = 0;
        for (let i = 0; i <= this.y; i++) {
            cumulativeRowGap += rowGaps[i];
        }

        // Sum up all column gaps before the statement
        let cumulativeColumnGap = 0;
        for (let i = 0; i <= this.x; i++) {
            cumulativeColumnGap += columnGaps[i];
        }
        // Sum up the cell heights of every row above the statement
        let combinedPreviousCellHeight = 0;
        for (let i = 0; i < this.y; i++) {
            combinedPreviousCellHeight += cellHeights[i];
        }

        // Find pixel coordinates of top-left corner without gaps
        let xPos = this.x * backgroundCellSize * cellWidth;
        let yPos = backgroundCellSize * combinedPreviousCellHeight;

        // Find cumulative gaps left of and above statement
        let xGap = cumulativeColumnGap * backgroundCellSize;
        let yGap = cumulativeRowGap * backgroundCellSize;

        // Top-left corner pixel coordinates
        let xStart = xPos + xGap;
        let yStart = yPos + yGap;

        let xEnd = xStart + backgroundCellSize * cellWidth;
        let yEnd = yStart + backgroundCellSize * cellHeights[this.y];

        this.pixelCoords = [new Point(xStart, yStart), new Point(xEnd, yStart), new Point(xEnd, yEnd), new Point(xStart, yEnd)];
    }

    draw(VisualizationSettings) {
        // Draw background
        let background = roundedPolygonPath(this.pixelCoords, VisualizationSettings.cornerRadius, false)[0];
        this.svgPath = roundedPolygonPath(this.pixelCoords, VisualizationSettings.cornerRadius, false)[1];
        c.fillStyle = "rgb(245, 245, 245)";
        c.fill(background);
        c.strokeStyle = "rgba(130, 130, 130, 0.5)";
        c.stroke(background);

        // Get entity names and their colors
        let namesAndColors = this.getEntityNamesAndColors();

        // Find name indices in the text
        let nameIndices = [];
        for (let i = 0; i < namesAndColors.length; i++) {
            nameIndices.push(getIndicesOf(namesAndColors[i][0], this.text, false));
        }

        // Pointers
        let currentIndex = 0;
        let drawingName = false;
        let lengthSoFar = 0;
        let ongoingNameLengthsAndColors = [];

        // Draw each character in statement text
        for (let i = 0; i < this.textLines.length; i++) {
            for (let j = 0; j < this.textLines[i].length; j++) {
                let textX = this.pixelCoords[0].x + backgroundCellSize + lengthSoFar;
                let textY = this.pixelCoords[0].y + (2 + i) * backgroundCellSize;

                // Check if we are at the start of an entity name
                for (let k = 0; k < nameIndices.length; k++) {
                    for (let l = 0; l < nameIndices[k].length; l++) {
                        // A name starts at this index
                        if (currentIndex == nameIndices[k][l]) {
                            // Add name length and color to list of current names
                            ongoingNameLengthsAndColors.unshift([namesAndColors[k][0].length, namesAndColors[k][1]]);
                        }
                    }
                }

                // Sort names by (remaining) length
                ongoingNameLengthsAndColors.sort((a, b) => a[0] - b[0]);

                // If names start at this index draw the shortest (first) one
                if (ongoingNameLengthsAndColors.length > 0) {
                    c.fillStyle = ongoingNameLengthsAndColors[0][1];
                    drawingName = true;
                }

                // If we are no longer drawing an entity switch back to black
                if (!drawingName) c.fillStyle = "#000";

                // Check if we are drawing the name of a singleton set and update the font
                let drawingBold = false;
                if (c.fillStyle == "#ffffff") {
                    c.fillStyle = "#000";
                    c.font = boldFont;
                    drawingBold = true;
                }

                if (VisualizationSettings.textHighlight == "text") {
                    // Draw next character
                    c.fillText(this.textLines[i][j], textX, textY);

                    // Draw underline
                    if (drawingBold) {
                        c.fillRect(Math.round(textX), Math.round(textY + 1), Math.round(c.measureText(this.textLines[i][j]).width + 0.3), 1);
                    }
                }
                else if (VisualizationSettings.textHighlight == "background") {
                    if (drawingBold) {
                        // Draw next character
                        c.fillText(this.textLines[i][j], textX, textY);

                        // Draw underline
                        c.fillRect(Math.round(textX), Math.round(textY + 1), Math.round(c.measureText(this.textLines[i][j]).width + 0.3), 1);
                    }
                    else {
                        if (drawingName) {
                            // Draw background rectangle
                            let height = c.measureText("G").actualBoundingBoxAscent + 4;
                            let color = c.fillStyle;
                            c.fillStyle = lightenRGB(hexToRgb(c.fillStyle), 0.7);
                            c.fillRect(Math.round(textX), Math.round(textY - height) + 2, Math.round(c.measureText(this.textLines[i][j]).width + 0.3), Math.round(height));
                            c.fillStyle = color;
                        }
                    }
                }
                else if (VisualizationSettings.textHighlight == "none") {
                    // Draw next character
                    c.fillStyle = "#000";
                    c.font = font;
                    c.fillText(this.textLines[i][j], textX, textY);
                }

                // Reset font if needed
                if (drawingBold) {
                    c.font = font;
                    c.fillStyle = "rgb(255, 255, 255)";
                }

                // Update pointers
                ongoingNameLengthsAndColors.forEach(e => {
                    e[0]--; // Decrease remaining length of all current names
                });

                // Remove names from the list when they are done
                ongoingNameLengthsAndColors = ongoingNameLengthsAndColors.filter(e => e[0] != 0);

                // No names left to draw at this index
                if (ongoingNameLengthsAndColors.length == 0) {
                    drawingName = false;
                    drawingBold = false;
                }
                else {
                    c.fillStyle = ongoingNameLengthsAndColors[0][1];
                }

                // Move on to next character
                currentIndex++;
                lengthSoFar += c.measureText(this.textLines[i][j]).width;
            }

            // Reset length after each line
            lengthSoFar = 0;
        }

        // If the highlight style is "background" draw all the text separately after all the backgrounds
        if (VisualizationSettings.textHighlight == "background") {
            c.fillStyle = "#000";
            let currentEntityNameColor = "#000";
            currentIndex = 0;

            for (let i = 0; i < this.textLines.length; i++) {
                for (let j = 0; j < this.textLines[i].length; j++) {
                    let textX = this.pixelCoords[0].x + backgroundCellSize + lengthSoFar;
                    let textY = this.pixelCoords[0].y + (2 + i) * backgroundCellSize;

                    // Check if we are at the start of an entity name
                    for (let k = 0; k < nameIndices.length; k++) {
                        for (let l = 0; l < nameIndices[k].length; l++) {
                            // A name starts at this index
                            if (currentIndex == nameIndices[k][l]) {
                                // Add name length and color to list of current names
                                ongoingNameLengthsAndColors.unshift([namesAndColors[k][0].length, namesAndColors[k][1]]);
                            }
                        }
                    }

                    // Sort names by (remaining) length
                    ongoingNameLengthsAndColors.sort((a, b) => a[0] - b[0]);

                    // If names start at this index update the drawingName pointer
                    if (ongoingNameLengthsAndColors.length > 0) {
                        currentEntityNameColor = ongoingNameLengthsAndColors[0][1];
                        drawingName = true;
                    }

                    // Check if we are drawing the name of a singleton
                    let drawingBold = false;
                    if (currentEntityNameColor == "rgb(255, 255, 255)") {
                        drawingBold = true;
                    }

                    // Draw a white outline only for non-singleton names
                    if (drawingName && !drawingBold) {
                        c.strokeStyle = "rgb(245, 245, 245)";
                        c.lineWidth = 1;
                        c.strokeText(this.textLines[i][j], textX, textY);
                    }
                    // Draw next character
                    c.fillText(this.textLines[i][j], textX, textY);

                    // 
                    if (drawingBold) {
                        currentEntityNameColor = "rgb(255, 255, 255)";
                    }

                    // Update pointers
                    ongoingNameLengthsAndColors.forEach(e => {
                        e[0]--; // Decrease remaining length of all current names
                    });

                    // Remove names from the list when they are done
                    ongoingNameLengthsAndColors = ongoingNameLengthsAndColors.filter(e => e[0] != 0);

                    // No names left to draw at this index
                    if (ongoingNameLengthsAndColors.length == 0) {
                        drawingName = false;
                        drawingBold = false;
                    }
                    else {
                        currentEntityNameColor = ongoingNameLengthsAndColors[0][1];
                    }

                    // Move on to next character
                    currentIndex++;
                    lengthSoFar += c.measureText(this.textLines[i][j]).width;
                }

                // Reset length after each line
                lengthSoFar = 0;
            }
        }
    }
}
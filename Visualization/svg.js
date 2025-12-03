function exportToSVG(VisualizationSettings) {
    // Create svg and set parameters
    const svgNS = "http://www.w3.org/2000/svg";
    const svg = document.createElementNS(svgNS, "svg");
    createSVG(svg, svgNS);

    // Draw elements
    drawEntities(svg, svgNS, VisualizationSettings);
    drawStatements(svg, svgNS, VisualizationSettings);

    // Serialize and download
    const svgData = new XMLSerializer().serializeToString(svg);
    const blob = new Blob([svgData], { type: "image/svg+xml;charset=utf-8" });
    const url = URL.createObjectURL(blob);

    const link = document.createElement("a");
    link.href = url;
    link.download = "solution.svg";
    link.click();
}

function createSVG(svg, svgNS) {
    svg.setAttribute("xmlns", svgNS);
    svg.setAttribute("width", canvas.width);
    svg.setAttribute("height", canvas.height);

    // Add white background
    const background = document.createElementNS(svgNS, "rect");
    background.setAttribute("x", 0);
    background.setAttribute("y", 0);
    background.setAttribute("width", canvas.width);
    background.setAttribute("height", canvas.height);
    background.setAttribute("fill", "white");
    svg.appendChild(background);
}

function drawEntities(svg, svgNS, VisualizationSettings) {
    // Draw entities in order of their starting y coordinates
    if (VisualizationSettings.entityRender == "transparent") entityRects.sort((a, b) => a.pixelCoords[0].y - b.pixelCoords[0].y);

    // Create entity groups
    const entityGroups = new Map();
    entityRects.forEach(entity => {
        const entityGroup = document.createElementNS(svgNS, "g");
        entityGroup.setAttribute("id", `entity-${entity.id}`);
        entityGroup.setAttribute("font-size", `${backgroundCellSize}px`);
        entityGroup.setAttribute("font-family", "Cambria");
        entityGroups.set(entity.id, entityGroup);
    });

    // Draw each entity
    entityRects.forEach(entity => {
        drawEntity(entity, entityGroups.get(entity.id), svgNS, VisualizationSettings);
        if (VisualizationSettings.headersIncluded) labelEntity(entity, entityGroups.get(entity.id), svgNS);
        svg.appendChild(entityGroups.get(entity.id));
    });
}

function drawEntity(entity, entityGroup, svgNS, VisualizationSettings) {
    // Only draw non-singleton entities or singleton copies
    if (!entity.singleton) {
        const color = entity.colors[entity.statements.length > 1 ? 0 : (entity.deleted.includes(true) ? entity.deleted.indexOf(true) : 0)];

        // Shadow
        if (VisualizationSettings.enableShadow) {
            const shadowPath = document.createElementNS(svgNS, "path");
            shadowPath.setAttribute("d", entity.svgShadowPath);
            shadowPath.setAttribute("fill", "rgb(50, 50, 50)");
            shadowPath.setAttribute("fill-opacity", 0.5);
            entityGroup.appendChild(shadowPath);
        }

        // Polygon
        const path = document.createElementNS(svgNS, "path");
        path.setAttribute("d", entity.svgPath);
        path.setAttribute("fill", color);

        if (VisualizationSettings.entityRender == "transparent") {
            path.setAttribute("fill-opacity", 0.15);
            path.setAttribute("stroke", color);
        }

        // Draw borders
        if (VisualizationSettings.enableOutline) {
            if (VisualizationSettings.outlinesUseEntityColor) {
                path.setAttribute("stroke", darkenRGB(color, 0.7));
            }
            else {
                path.setAttribute("stroke", rgbToRgba(VisualizationSettings.outlineColor, 0.5));
            }

            if (!entity.deleted.includes(true) && VisualizationSettings.outlineNonRepeated) {
                path.setAttribute("stroke-width", Number(VisualizationSettings.outlineWeight) + 1);
            }
            else if (entity.deleted.includes(true) && VisualizationSettings.outlineRepeated) {
                if (VisualizationSettings.dashRepeated) {
                    path.setAttribute("stroke-width", Number(VisualizationSettings.outlineWeight) + 1);
                    path.setAttribute("stroke-dasharray", "6,2");
                }
                else {
                    path.setAttribute("stroke-width", Number(VisualizationSettings.outlineWeight) + 1);
                }
            }
        }

        entityGroup.appendChild(path);
    }
}

function labelEntity(entity, entityGroup, svgNS) {
    // Only label non-singleton entities or singleton copies
    if (!entity.singleton) {
        const width = entity.pixelCoords[1].x - entity.pixelCoords[0].x;

        // Headers
        let headerIndex = 0;
        for (let i = 0; i < entity.headers.length; i++) {
            if (entity.statements.length > 1 || entity.deleted[i]) {
                const headerHeight = 2 * backgroundCellSize;
                const headerY = entity.pixelCoords[0].y + headerIndex * headerHeight;

                // Background rect
                const headerBackground = document.createElementNS(svgNS, "path");
                headerBackground.setAttribute("d", entity.svgHeaderOutlines[i]);
                headerBackground.setAttribute("fill", entity.colors[i]);

                // Area behind name
                const nameBackground = document.createElementNS(svgNS, "path");
                nameBackground.setAttribute("d", entity.svgNameOutlines[i]);
                nameBackground.setAttribute("fill", entity.colors[i]);

                entityGroup.appendChild(headerBackground);

                // Crosshatch if marked as deleted
                if (entity.deleted[i]) crosshatchHeader(entityGroup, svgNS, entity, i, width, headerY, headerHeight);

                entityGroup.appendChild(nameBackground);

                // Header text
                const text = document.createElementNS(svgNS, "text");
                text.setAttribute("x", entity.pixelCoords[0].x + backgroundCellSize + 1);
                text.setAttribute("y", headerY + 1.25 * backgroundCellSize + 1);
                text.setAttribute("fill", "white");
                text.textContent = entity.displayHeaders[i];
                entityGroup.appendChild(text);

                headerIndex++;
            }
        }
    }
}

function drawStatements(svg, svgNS, VisualizationSettings) {
    // Draw statements
    statementCells.forEach(statement => {
        // Group elements in this statement
        const statementGroup = document.createElementNS(svgNS, "g");
        statementGroup.setAttribute("id", `statement-${statement.id || "group"}`);
        statementGroup.setAttribute("font-size", `${backgroundCellSize}px`);
        statementGroup.setAttribute("font-family", "Cambria");

        const xStart = statement.pixelCoords[0].x;
        const yStart = statement.pixelCoords[0].y;

        // Draw background rectangle
        const path = document.createElementNS(svgNS, "path");
        path.setAttribute("d", statement.svgPath);
        path.setAttribute("fill", "rgb(245, 245, 245)");
        path.setAttribute("stroke", "rgb(130, 130, 130)");
        path.setAttribute("stroke-opacity", 0.5);
        path.setAttribute("stroke-width", Number(VisualizationSettings.outlineWeight) + 1);
        statementGroup.appendChild(path);

        // Get entity names and their positions
        let namesAndColors = statement.getEntityNamesAndColors();
        let nameIndices = [];

        for (let i = 0; i < namesAndColors.length; i++) {
            nameIndices.push(getIndicesOf(namesAndColors[i][0], statement.text, false));
        }

        // Store text to be draw after all entity name backgrounds (for highlight type "background")
        let statementText = [];

        // Pointers
        let currentIndex = 0;
        let drawingName = false;
        let lengthSoFar = 0;
        let ongoingNameLengthsAndColors = [];

        // Draw each character in statement text
        for (let i = 0; i < statement.textLines.length; i++) {
            for (let j = 0; j < statement.textLines[i].length; j++) {

                // Check if we are at the start of an entity name
                for (let k = 0; k < nameIndices.length; k++) {
                    for (let l = 0; l < nameIndices[k].length; l++) {
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
                    fillColor = ongoingNameLengthsAndColors[0][1];
                    drawingName = true;
                }

                // If we are not drawing a name, use black
                if (!drawingName) fillColor = "rgb(0, 0, 0)";

                // If singleton (white), override to black and bold
                let drawingBold = false;
                let fontWeight = "normal";
                if (fillColor == "rgb(255, 255, 255)") {
                    fillColor = "rgb(0, 0, 0)";
                    fontWeight = "bolder";
                    drawingBold = true;
                } else if (fillColor !== "rgb(0, 0, 0)") {
                    fontWeight = "bold";
                }

                const width = c.measureText(statement.textLines[i][j]).width + 0.3;

                // Background behind the text
                const height = c.measureText("G").actualBoundingBoxAscent + 4;
                const textBackground = document.createElementNS(svgNS, "rect");
                textBackground.setAttribute("x", xStart + backgroundCellSize + lengthSoFar);
                textBackground.setAttribute("y", yStart + (2 + i) * backgroundCellSize - height + 2);
                textBackground.setAttribute("width", width);
                textBackground.setAttribute("height", height);
                textBackground.setAttribute("fill", lightenRGB(fillColor, 0.7));
                textBackground.textContent = statement.textLines[i][j];

                // Text
                const textElem = document.createElementNS(svgNS, "text");
                textElem.setAttribute("x", xStart + backgroundCellSize + lengthSoFar);
                textElem.setAttribute("y", yStart + (2 + i) * backgroundCellSize);
                textElem.textContent = statement.textLines[i][j];

                // Underline
                const underline = document.createElementNS(svgNS, "line");
                underline.setAttribute("x1", xStart + backgroundCellSize + lengthSoFar);
                underline.setAttribute("x2", xStart + backgroundCellSize + lengthSoFar + width);
                underline.setAttribute("y1", yStart + (2 + i) * backgroundCellSize + 1);
                underline.setAttribute("y2", yStart + (2 + i) * backgroundCellSize + 1);
                underline.setAttribute("stroke", "#000");
                underline.setAttribute("stroke-width", "1");


                if (VisualizationSettings.textHighlight == "text") {
                    // Draw text
                    textElem.setAttribute("fill", fillColor);
                    textElem.setAttribute("font-weight", fontWeight);
                    statementGroup.appendChild(textElem);

                    // Draw underline
                    if (drawingBold) {
                        statementGroup.appendChild(underline);
                    }
                }
                else if (VisualizationSettings.textHighlight == "background") {
                    if (drawingName && !drawingBold) {
                        // Draw text background
                        statementGroup.appendChild(textBackground);
                    }

                    // Draw text
                    textElem.setAttribute("fill", "#000");
                    textElem.setAttribute("font-weight", fontWeight);
                    textElem.setAttribute("stroke", "rgb(245, 245, 245)");
                    textElem.setAttribute("stroke-width", 1);
                    textElem.setAttribute("paint-order", "stroke");
                    statementText.push(textElem);

                    // Draw underline
                    if (drawingBold) {
                        statementGroup.appendChild(underline);
                    }
                }
                else if (VisualizationSettings.textHighlight == "none") {
                    // Draw text
                    textElem.setAttribute("fill", "#000");
                    textElem.setAttribute("font-weight", "normal");
                    statementGroup.appendChild(textElem);
                }

                // Reset font if needed
                if (drawingBold) {
                    fillColor = "rgb(255, 255, 255)";
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
                    fillColor = ongoingNameLengthsAndColors[0][1];
                }

                // Move on to next character
                currentIndex++;
                lengthSoFar += c.measureText(statement.textLines[i][j]).width;
            }

            // Reset length after each line
            lengthSoFar = 0;
        }

        // Draw all text on top of backgrounds if necessary
        statementText.forEach(t => statementGroup.appendChild(t));

        svg.appendChild(statementGroup);
    });
}

function crosshatchHeader(entityGroup, svgNS, entity, i, width, headerY, headerHeight) {
    const clipId = `clip-${entity.id}-${i}`;

    // Define a clipping path to restrict hatching to header area
    const clipPath = document.createElementNS(svgNS, "clipPath");
    clipPath.setAttribute("id", clipId);

    // Define what area to clip
    const clipRect = document.createElementNS(svgNS, "rect");
    clipRect.setAttribute("x", entity.pixelCoords[0].x + 5);
    clipRect.setAttribute("y", headerY + 1);
    clipRect.setAttribute("width", width - 7);
    clipRect.setAttribute("height", headerHeight - 1);
    clipPath.appendChild(clipRect);

    entityGroup.appendChild(clipPath);

    // Create the group for hatch lines
    const hatchGroup = document.createElementNS(svgNS, "g");
    hatchGroup.setAttribute("clip-path", `url(#${clipId})`);
    hatchGroup.setAttribute("stroke", "white");
    hatchGroup.setAttribute("stroke-width", "0.75");

    const spacing = 5;

    // Forward-slash lines (/)
    for (let x = -headerHeight; x < width + headerHeight; x += spacing) {
        const line = document.createElementNS(svgNS, "line");
        line.setAttribute("x1", entity.pixelCoords[0].x + x);
        line.setAttribute("y1", headerY);
        line.setAttribute("x2", entity.pixelCoords[0].x + x + headerHeight);
        line.setAttribute("y2", headerY + headerHeight);
        hatchGroup.appendChild(line);
    }

    // Backslash lines (\)
    for (let x = -headerHeight; x < width + headerHeight; x += spacing) {
        const line = document.createElementNS(svgNS, "line");
        line.setAttribute("x1", entity.pixelCoords[0].x + x + headerHeight);
        line.setAttribute("y1", headerY);
        line.setAttribute("x2", entity.pixelCoords[0].x + x);
        line.setAttribute("y2", headerY + headerHeight);
        hatchGroup.appendChild(line);
    }

    entityGroup.appendChild(hatchGroup);
}
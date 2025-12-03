// Split text into lines to fit into a statement cell
function splitTextIntoLines(text, lineLength) {
    // Ensure the correct font is used when measuring the text width
    c.font = font;

    let lines = [];

    let words = text.split(" ");
    let line = "";

    // Build line word by word
    for (let word of words) {
        let testLine = line + word;
        let metrics = c.measureText(testLine);

        if (metrics.width >= lineLength && line !== "") {
            lines.push(line);
            line = word + " ";
        } else {
            line = testLine + " ";
        }
    }

    lines.push(line);

    // Remove leading spaces
    for (let i = 0; i < lines.length; i++) {
        if (lines[i][0] == " ") lines[i] = lines[i].slice(1);
    }

    return lines;
}

// Find all indices of a string in a text
function getIndicesOf(searchStr, str, caseSensitive) {
    let searchStrLen = searchStr.length;
    if (searchStrLen == 0) {
        return [];
    }
    let startIndex = 0, index, indices = [];
    if (!caseSensitive) {
        str = str.toLowerCase();
        searchStr = searchStr.toLowerCase();
    }
    while ((index = str.indexOf(searchStr, startIndex)) > -1) {
        indices.push(index);
        startIndex = index + searchStrLen;
    }
    return indices;
}

// Get all variations from an entity name
function extractEntityNames(input) {
    const names = [];

    // Match the name before any ( or [ with a space
    const mainMatch = input.match(/^([^(^\[]+?)(?=\s*(\(|\[|$))/);
    if (mainMatch) {
        names.push(mainMatch[1].trim());
    }

    // Match names inside parentheses only if preceded by a space
    const parenMatch = input.match(/(?<=\s)\(([^)]+)\)/);
    if (parenMatch) {
        names.push(...parenMatch[1].split(',').map(s => s.trim()));
    }

    // Match names inside square brackets only if preceded by a space
    const bracketMatch = input.match(/(?<=\s)\[([^\]]+)\]/);
    if (bracketMatch) {
        names.push(...bracketMatch[1].split(',').map(s => s.trim()));
    }

    return names;
}

// Convert an entity's name in the data into a version to be displayed in the header
function preprocessEntityName(input, width) {
    // Ensure the correct font is used when measuring the text width
    c.font = font;

    // Remove invisible name variations
    let fullDisplayName = input.replace(/\s*\[[^\]]*\]/g, '');

    // Store the visible portion of the name
    let visible = "";

    for (let i = 0; i < fullDisplayName.length; i++) {
        let char = fullDisplayName[i];
        let testVisible = visible + char;
        let metrics = c.measureText(testVisible);

        // Truncate name if it is too long to fit in the header
        if (metrics.width >= width - 2 * backgroundCellSize) {
            visible = visible.substring(0, visible.length - 2) + "...";
            break;
        } else {
            visible = testVisible;
        }
    }

    return visible;
}
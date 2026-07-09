const SOFT_HYPHEN = "\u00AD";

const hyphenateWord = createHyphenator(hyphenationPatternsEnUs, {
    hyphenChar: SOFT_HYPHEN,
    minWordLength: 5,
    html: false
});

// Split text into lines to fit into a statement cell
function splitTextIntoLines(text, lineLength) {
    c.font = font;

    const lines = [];
    let line = "";

    const fits = (value) =>
        c.measureText(value).width <= lineLength;

    function pushLine() {
        if (line !== "") {
            lines.push(line + " ");
            line = "";
        }
    }

    // Split a word only at valid hyphenation positions
    function placeWord(word) {
        const normalCandidate = line === "" ? word : line + " " + word;

        // Complete word fits
        if (fits(normalCandidate)) {
            line = normalCandidate;
            return;
        }

        // Library returns something like: "extra\u00ADor\u00ADdi\u00ADnary"
        const pieces = hyphenateWord(word).split(SOFT_HYPHEN);
        let pieceIndex = 0;

        while (pieceIndex < pieces.length) {
            const prefix = line === "" ? "" : line + " ";
            let piece = "";
            let bestEndIndex = -1;

            // Find the longest valid hyphenation prefix that fits
            for (let i = pieceIndex; i < pieces.length; i++) {
                piece += pieces[i];

                const isLastPiece = i === pieces.length - 1;
                const candidate = prefix + piece + (isLastPiece ? "" : "-");

                if (fits(candidate)) {
                    bestEndIndex = i;
                } else {
                    break;
                }
            }

            // If nothing fits in the remaining space, start a new line
            if (bestEndIndex === -1) {
                if (line !== "") {
                    pushLine();
                    continue;
                }

                // Fallback for an unbreakable segment wider than the line:
                // split character by character
                let partial = "";

                for (const character of pieces[pieceIndex]) {
                    if (fits(partial + character + "-")) {
                        partial += character;
                    } else {
                        break;
                    }
                }

                if (partial === "") {
                    partial = pieces[pieceIndex][0];
                }

                lines.push(partial + "-");
                pieces[pieceIndex] = pieces[pieceIndex].slice(partial.length);
                continue;
            }

            piece = pieces
                .slice(pieceIndex, bestEndIndex + 1)
                .join("");

            const hasRemainingPieces = bestEndIndex < pieces.length - 1;
            line = prefix + piece + (hasRemainingPieces ? "-" : "");

            pieceIndex = bestEndIndex + 1;

            // A hyphenated part finishes this line
            if (hasRemainingPieces) {
                pushLine();
            }
        }
    }

    for (const word of text.split(" ")) {
        placeWord(word);
    }

    pushLine();
    return lines;
}

// Find all variations of a string that can be obtained as a result of hyphenation
function getHyphenatedVariations(str) {
    let syllables = hyphenateWord(str).split(SOFT_HYPHEN);
    let variations = [str];

    for (let i = 0; i < syllables.length - 1; i++) {
        let variation = "";

        for (let j = 0; j <= i; j++) {
            variation += syllables[j];
        }

        variation += "- ";

        for (let j = i + 1; j < syllables.length; j++) {
            variation += syllables[j];
        }

        variations.push(variation);
    }

    return variations;
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
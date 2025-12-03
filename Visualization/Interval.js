class Interval {
    constructor(start, end, other, side, entity) {
        this.start = start; // Start coordinate (x or y depending on side)
        this.end = end;     // End coordinate (x or y depending on side)
        this.otherCoord = other; // The fixed coordinate (y for top/bottom, x for left/right)
        this.side = side; // 'top', 'right', 'bottom', 'left'
        this.margin = 1; // Margin to apply when positioning entities
        this.entity = entity; // Reference to the entity this interval belongs to
        this.isTopLeft = false; // Whether this interval is the top-left interval of its entity
    }

    // Check if this interval overlaps with another
    overlaps(other) {
        if (this.entity.singleton || other.entity.singleton) return false;

        // Allow left-right and top-bottom overlaps as well as same-side
        const oppositeSides = {
            left: 'right',
            right: 'left',
            top: 'bottom',
            bottom: 'top'
        };

        // Check if sides are the same or opposite
        if (
            this.side !== other.side &&
            oppositeSides[this.side] !== other.side
        ) {
            return false;
        }
        return this.start <= other.end && this.end >= other.start;
    }

    pixelOverlaps(other) {
        if (this.entity.singleton || other.entity.singleton) return false;

        // Allow left-right and top-bottom overlaps as well as same-side
        const oppositeSides = {
            left: 'right',
            right: 'left',
            top: 'bottom',
            bottom: 'top'
        };

        // Check if sides are the same or opposite
        if (
            this.side !== other.side &&
            oppositeSides[this.side] !== other.side
        ) {
            return false;
        }

        return (this.startPixel < other.endPixel && this.endPixel > other.startPixel) || this.cornerOverlaps(other);
    }

    cornerOverlaps(other) {
        const oppositeSides = {
            left: 'right',
            right: 'left',
            top: 'bottom',
            bottom: 'top'
        };

        if (oppositeSides[this.side] !== other.side) return false;

        let thisStartTouchesOtherEnd = this.startPixel == other.endPixel;
        let thisEndTouchesOtherStart = this.endPixel == other.startPixel;

        if (!thisStartTouchesOtherEnd && !thisEndTouchesOtherStart) return false;

        let thisCornerPart = thisStartTouchesOtherEnd ? this.start : this.end;
        let otherCornerPart = thisStartTouchesOtherEnd ? other.end : other.start;

        let overlappingCells = false;

        if (this.side == "left") {
            if ([...other.entity.cells].some(point =>
                point.y == thisCornerPart &&
                point.x == this.otherCoord
            ) && [...other.entity.cells].some(point =>
                point.y == thisCornerPart &&
                point.x == this.otherCoord - 1
            ) &&
                [...this.entity.cells].some(point =>
                    point.y == otherCornerPart &&
                    point.x == other.otherCoord
                ) && [...this.entity.cells].some(point =>
                    point.y == otherCornerPart &&
                    point.x == other.otherCoord + 1
                )) {
                overlappingCells = true;
            }
        }
        if (this.side == "right") {
            if ([...other.entity.cells].some(point =>
                point.y == thisCornerPart &&
                point.x == this.otherCoord
            ) && [...other.entity.cells].some(point =>
                point.y == thisCornerPart &&
                point.x == this.otherCoord + 1
            ) &&
                [...this.entity.cells].some(point =>
                    point.y == otherCornerPart &&
                    point.x == other.otherCoord
                ) && [...this.entity.cells].some(point =>
                    point.y == otherCornerPart &&
                    point.x == other.otherCoord - 1
                )) {
                overlappingCells = true;
            }
        }
        if (this.side == "top") {
            if ([...other.entity.cells].some(point =>
                point.x == thisCornerPart &&
                point.y == this.otherCoord
            ) && [...other.entity.cells].some(point =>
                point.x == thisCornerPart &&
                point.y == this.otherCoord - 1
            ) &&
                [...this.entity.cells].some(point =>
                    point.x == otherCornerPart &&
                    point.y == other.otherCoord
                ) && [...this.entity.cells].some(point =>
                    point.x == otherCornerPart &&
                    point.y == other.otherCoord + 1
                )) {
                overlappingCells = true;
            }
        }
        if (this.side == "bottom") {
            if ([...other.entity.cells].some(point =>
                point.x == thisCornerPart &&
                point.y == this.otherCoord
            ) && [...other.entity.cells].some(point =>
                point.x == thisCornerPart &&
                point.y == this.otherCoord + 1
            ) &&
                [...this.entity.cells].some(point =>
                    point.x == otherCornerPart &&
                    point.y == other.otherCoord
                ) && [...this.entity.cells].some(point =>
                    point.x == otherCornerPart &&
                    point.y == other.otherCoord - 1
                )) {
                overlappingCells = true;
            }
        }

        return overlappingCells;
    }

    // Get the length of the interval
    length() {
        return this.end - this.start;
    }

    // Set the other coordinate of the interval on the canvas
    setOther(value) {
        this.otherPixel = value;
    }

    setStart(value) {
        this.startPixel = value;
    }

    setEnd(value) {
        this.endPixel = value;
    }

    setTopLeft(isTopLeft, headersIncluded) {
        this.isTopLeft = isTopLeft;
        if (headersIncluded) this.margin += 2;
    }
}
function getSegments(e1) {
    const segs = [];
    const { top, right, bottom, left } = e1.intervals;

    // Horizontal sides: top & bottom
    for (const s of top) {
        segs.push({ x1: s.start, y1: s.otherCoord, x2: s.end, y2: s.otherCoord });
    }
    for (const s of bottom) {
        segs.push({ x1: s.start, y1: s.otherCoord, x2: s.end, y2: s.otherCoord });
    }

    // Vertical sides: left & right
    for (const s of left) {
        segs.push({ x1: s.otherCoord, y1: s.start, x2: s.otherCoord, y2: s.end });
    }
    for (const s of right) {
        segs.push({ x1: s.otherCoord, y1: s.start, x2: s.otherCoord, y2: s.end });
    }

    return segs;
}

function pointSegmentDistance(px, py, x1, y1, x2, y2) {
    const dx = x2 - x1;
    const dy = y2 - y1;
    const lengthSq = dx * dx + dy * dy;
    if (lengthSq === 0) return Math.hypot(px - x1, py - y1); // degenerate segment

    // projection of point onto segment, clamped 0–1
    const t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lengthSq));
    const projX = x1 + t * dx;
    const projY = y1 + t * dy;

    return Math.hypot(px - projX, py - projY);
}

function segmentDistance(x1, y1, x2, y2, x3, y3, x4, y4) {
    // endpoints of seg1: (x1,y1)-(x2,y2)
    // endpoints of seg2: (x3,y3)-(x4,y4)
    return Math.min(
        pointSegmentDistance(x1, y1, x3, y3, x4, y4),
        pointSegmentDistance(x2, y2, x3, y3, x4, y4),
        pointSegmentDistance(x3, y3, x1, y1, x2, y2),
        pointSegmentDistance(x4, y4, x1, y1, x2, y2)
    );
}

// Calculate polygon distance between entities.
// Note: this function uses the intervals of the entities so make sure that these are computed before using it!
function polygonDistance(e1, e2) {
    // if they overlap distance is 0, for practical reasons it is 1e-6
    if (doEntitiesOverlap(e1, e2))  {
        return Math.abs(entityRects.indexOf(e1) - entityRects.indexOf(e2)) * 0.01;
    }

    const segs1 = getSegments(e1);
    const segs2 = getSegments(e2);
    let minDist = Infinity;

    for (const s1 of segs1) {
        for (const s2 of segs2) {
            const d = segmentDistance(s1.x1, s1.y1, s1.x2, s1.y2, s2.x1, s2.y1, s2.x2, s2.y2);
            if (d < minDist) minDist = d;
            if (minDist === 0) return Math.abs(entityRects.indexOf(e1) - entityRects.indexOf(e2)) * 0.01; // early exit
        }
    }

    return minDist;
}

// Compute distance between polygons that of repeating entities
function repeatingPolygonsDistance(e1, e2, repeatingMap) {
    distances = []
    if (!repeatingMap.has(e1.headers[0])) { // then e2 repeates
        // compute distances between all polygons of e2 and the polygon of e1
        for (const p2 of repeatingMap.get(e2.headers[0])) {
            distances.push(polygonDistance(e1, p2));
        }
    } else if (!repeatingMap.has(e2.headers[0])) { // then e1 repeats
        // compute distances between all polygons of e1 and the polygon of e2
        for (const p1 of repeatingMap.get(e1.headers[0])) {
            distances.push(polygonDistance(p1, e2));
        }
    } else {
        // both repeat
        for (const p1 of repeatingMap.get(e1.headers[0])) {
            for (const p2 of repeatingMap.get(e2.headers[0])) {
                distances.push(polygonDistance(p1, p2));
            }
        }
    }

    return Math.min(...distances);
}

// Precompute distances between polygons once and use the spatial matrix for every time they are needed
function buildSpatialMatrix(entities, repeatingMap) {
    const n = entities.length;
    const M = Array.from({ length: n }, () => new Array(n).fill(0));

    for (let i = 0; i < n; i++) {
        for (let j = i + 1; j < n; j++) {
            let ds = 0;
            if (repeatingMap.has(entities[i].headers[0]) || repeatingMap.has(entities[j].headers[0])) {
                ds = repeatingPolygonsDistance(entities[i], entities[j], repeatingMap);
            } else {
                ds = polygonDistance(entities[i], entities[j]);
            }
            M[i][j] = ds;
            M[j][i] = ds; // symmetry
        }
    }
    return M;
}

// Check if two entities overlap, now working for polygons as well
function doEntitiesOverlap(e1, e2) {
    // collect all Y values
    const e1Ys = e1.coords.map(c => c.y);
    const e2Ys = e2.coords.map(c => c.y);

    // find intersection
    const intersectionYs = e1Ys.filter(y => e2Ys.includes(y));

    // for each intersecting y, find xStart and xEnd in both
    for (const y of intersectionYs) {
        // filter all points at this Y for both entities
        const pointsE1 = e1.coords.filter(p => p.y === y);
        const pointsE2 = e2.coords.filter(p => p.y === y);

        // find min and max X for each entity at this row
        const xStartE1 = Math.min(...pointsE1.map(p => p.x));
        const xEndE1 = Math.max(...pointsE1.map(p => p.x));

        const xStartE2 = Math.min(...pointsE2.map(p => p.x));
        const xEndE2 = Math.max(...pointsE2.map(p => p.x));

        // e1 and e2 overlap on row y
        if (!(xEndE1 < xStartE2 || xEndE2 < xStartE1)) {
            return true;
        }
    }

    return false;
}

// Build an overlap graph: graph[i] is an array of indices of entities overlapping with entity i
function buildOverlapGraph(entities) {
    let n = entities.length;
    let graph = Array.from({ length: n }, () => []);
    for (let i = 0; i < n; i++) {
        for (let j = i + 1; j < n; j++) {
            if (doEntitiesOverlap(entities[i], entities[j])) {
                graph[i].push(j);
                graph[j].push(i);
            }
        }
    }
    return graph;
}

// Stack entities
function computeStacking(entities) {
    let nonPlacedEntities = entities.slice();
    let stackedEntities = [];

    while (nonPlacedEntities.length > 0) {
        stackedEntities.push(stackEntity(nonPlacedEntities));
    }

    for (let i = 0; i < entities.length; i++) {
        entities[i] = stackedEntities[i];
    }
}

// Choose an entity to stack next
function stackEntity(nonPlacedEntities) {
    let counts = [];
    for (const entity of nonPlacedEntities) {
        let remainingEntities = nonPlacedEntities.filter(e => e !== entity);
        counts.push({ entity: entity, count: countCoveredIntervals(entity, remainingEntities) });
    }

    counts.sort((a, b) => {
        // Sort by count (increasing)
        if (a.count !== b.count) {
            return a.count - b.count;
        }

        // If counts are equal, sort by number of intervals (decreasing)
        const aIntervals = a.entity.intervals.top.length + a.entity.intervals.bottom.length + a.entity.intervals.left.length + a.entity.intervals.right.length;
        const bIntervals = b.entity.intervals.top.length + b.entity.intervals.bottom.length + b.entity.intervals.left.length + b.entity.intervals.right.length;
        if (aIntervals !== bIntervals) {
            return bIntervals - aIntervals;
        }

        // If intervals also equal, sort by size (decreasing)
        const aCells = a.entity.cells.size;
        const bCells = b.entity.cells.size;
        return bCells - aCells;
    });

    // Remove and return chosen entity
    nonPlacedEntities.splice(nonPlacedEntities.indexOf(counts[0].entity), 1);
    if (counts[0].count > 0) {
        for (const side in counts[0].entity.intervals) {
            for (const interval of counts[0].entity.intervals[side]) {
                if (intervalIsCovered(interval, nonPlacedEntities)) console.log(interval);
            }
        }
    }

    return counts[0].entity;
}

// Count the number of covered intervals for this entity
function countCoveredIntervals(entity, remainingEntities) {
    let counter = 0;
    for (const side in entity.intervals) {
        for (const interval of entity.intervals[side]) {
            if (intervalIsCovered(interval, remainingEntities)) counter++;
        }
    }
    return counter;
}

// Check if an interval is covered
function intervalIsCovered(interval, remainingEntities) {
    let intStart = interval.start;
    let intEnd = interval.end;

    if (intStart == intEnd) return singleCellIntervalIsCovered(interval, remainingEntities);

    for (let i = intStart; i <= intEnd; i++) {
        let covered = false;
        for (const ent of remainingEntities) {
            if (intervalPartOverlaps(interval, i - intStart, ent)) {
                covered = true;
                break;
            }
        }
        if (!covered) return false;
    }

    return true;
}

function singleCellIntervalIsCovered(interval, remainingEntities) {
    let startCovered = false;
    let endCovered = false;

    for (const entity of remainingEntities) {
        if (interval.side == "top") {
            if ((([...entity.cells].some(point =>
                point.x == interval.start - 1 &&
                point.y == interval.otherCoord
            ) 
            && ![...interval.entity.cells].some(point =>
                point.x == interval.start - 1 &&
                point.y == interval.otherCoord - 1
            )) || ([...entity.cells].some(point =>
                point.x == interval.start - 1 &&
                point.y == interval.otherCoord
            ) 
            && [...entity.cells].some(point =>
                point.x == interval.start - 1 &&
                point.y == interval.otherCoord - 1
            )))
            && ([...entity.cells].some(point =>
                point.x == interval.start &&
                point.y == interval.otherCoord
            ) && [...entity.cells].some(point =>
                point.x == interval.start &&
                point.y == interval.otherCoord - 1
            ))) {
                startCovered = true;
            }

            if ((([...entity.cells].some(point =>
                point.x == interval.start + 1 &&
                point.y == interval.otherCoord
            ) 
            && ![...interval.entity.cells].some(point =>
                point.x == interval.start + 1 &&
                point.y == interval.otherCoord - 1
            )) || ([...entity.cells].some(point =>
                point.x == interval.start + 1 &&
                point.y == interval.otherCoord
            ) 
            && [...entity.cells].some(point =>
                point.x == interval.start + 1 &&
                point.y == interval.otherCoord - 1
            )))
            && ([...entity.cells].some(point =>
                point.x == interval.start &&
                point.y == interval.otherCoord
            ) && [...entity.cells].some(point =>
                point.x == interval.start &&
                point.y == interval.otherCoord - 1
            ))) {
                endCovered = true;
            }
        }

        if (interval.side == "bottom") {
            if ((([...entity.cells].some(point =>
                point.x == interval.start - 1 &&
                point.y == interval.otherCoord
            ) 
            && ![...interval.entity.cells].some(point =>
                point.x == interval.start - 1 &&
                point.y == interval.otherCoord + 1
            )) || ([...entity.cells].some(point =>
                point.x == interval.start - 1 &&
                point.y == interval.otherCoord
            ) 
            && [...entity.cells].some(point =>
                point.x == interval.start - 1 &&
                point.y == interval.otherCoord + 1
            )))
            && ([...entity.cells].some(point =>
                point.x == interval.start &&
                point.y == interval.otherCoord
            ) && [...entity.cells].some(point =>
                point.x == interval.start &&
                point.y == interval.otherCoord + 1
            ))) {
                startCovered = true;
            }

            if ((([...entity.cells].some(point =>
                point.x == interval.start + 1 &&
                point.y == interval.otherCoord
            ) 
            && ![...interval.entity.cells].some(point =>
                point.x == interval.start + 1 &&
                point.y == interval.otherCoord + 1
            )) || ([...entity.cells].some(point =>
                point.x == interval.start + 1 &&
                point.y == interval.otherCoord
            ) 
            && [...entity.cells].some(point =>
                point.x == interval.start + 1 &&
                point.y == interval.otherCoord + 1
            )))
            && ([...entity.cells].some(point =>
                point.x == interval.start &&
                point.y == interval.otherCoord
            ) && [...entity.cells].some(point =>
                point.x == interval.start &&
                point.y == interval.otherCoord + 1
            ))) {
                endCovered = true;
            }
        }

        if (interval.side == "left") {
            if ((([...entity.cells].some(point =>
                point.y == interval.start - 1 &&
                point.x == interval.otherCoord
            ) 
            && ![...interval.entity.cells].some(point =>
                point.y == interval.start - 1 &&
                point.x == interval.otherCoord - 1
            )) || ([...entity.cells].some(point =>
                point.y == interval.start - 1 &&
                point.x == interval.otherCoord
            )
            && [...entity.cells].some(point =>
                point.y == interval.start - 1 &&
                point.x == interval.otherCoord - 1
            )))
            && ([...entity.cells].some(point =>
                point.y == interval.start &&
                point.x == interval.otherCoord
            ) && [...entity.cells].some(point =>
                point.y == interval.start &&
                point.x == interval.otherCoord - 1
            ))) {
                startCovered = true;
            }

            if ((([...entity.cells].some(point =>
                point.y == interval.start + 1 &&
                point.x == interval.otherCoord
            ) 
            && ![...interval.entity.cells].some(point =>
                point.y == interval.start + 1 &&
                point.x == interval.otherCoord - 1
            )) || ([...entity.cells].some(point =>
                point.y == interval.start + 1 &&
                point.x == interval.otherCoord
            )
            && [...entity.cells].some(point =>
                point.y == interval.start + 1 &&
                point.x == interval.otherCoord - 1
            )))
            && ([...entity.cells].some(point =>
                point.y == interval.start &&
                point.x == interval.otherCoord
            ) && [...entity.cells].some(point =>
                point.y == interval.start &&
                point.x == interval.otherCoord - 1
            ))) {
                endCovered = true;
            }
        }

        if (interval.side == "right") {
            if ((([...entity.cells].some(point =>
                point.y == interval.start - 1 &&
                point.x == interval.otherCoord
            ) 
            && ![...interval.entity.cells].some(point =>
                point.y == interval.start - 1 &&
                point.x == interval.otherCoord + 1
            )) || ([...entity.cells].some(point =>
                point.y == interval.start - 1 &&
                point.x == interval.otherCoord
            ) 
            && [...entity.cells].some(point =>
                point.y == interval.start - 1 &&
                point.x == interval.otherCoord + 1
            )))
            && ([...entity.cells].some(point =>
                point.y == interval.start &&
                point.x == interval.otherCoord
            ) && [...entity.cells].some(point =>
                point.y == interval.start &&
                point.x == interval.otherCoord + 1
            ))) {
                startCovered = true;
            }

            if ((([...entity.cells].some(point =>
                point.y == interval.start + 1 &&
                point.x == interval.otherCoord
            ) 
            && ![...interval.entity.cells].some(point =>
                point.y == interval.start + 1 &&
                point.x == interval.otherCoord + 1
            )) || ([...entity.cells].some(point =>
                point.y == interval.start + 1 &&
                point.x == interval.otherCoord
            ) 
            && [...entity.cells].some(point =>
                point.y == interval.start + 1 &&
                point.x == interval.otherCoord + 1
            )))
            && ([...entity.cells].some(point =>
                point.y == interval.start &&
                point.x == interval.otherCoord
            ) && [...entity.cells].some(point =>
                point.y == interval.start &&
                point.x == interval.otherCoord + 1
            ))) {
                endCovered = true;
            }
        }
    }

    return startCovered && endCovered;
}

// Check if the i-th cell of this interval is covered 
function intervalPartOverlaps(interval, i, entity) {
    if (interval.side == "top") {
        if (i == 0) {
            return (([...entity.cells].some(point =>    // Case 2
                point.x == interval.start + i - 1 &&
                point.y == interval.otherCoord
            )
                && ![...interval.entity.cells].some(point =>
                    point.x == interval.start + i - 1 &&
                    point.y == interval.otherCoord - 1
                )
            ) || ([...entity.cells].some(point =>   // Case 1
                point.x == interval.start + i - 1 &&
                point.y == interval.otherCoord
            )
                && [...entity.cells].some(point =>
                    point.x == interval.start + i - 1 &&
                    point.y == interval.otherCoord - 1
                )
            )) // Same as internal
                && ([...entity.cells].some(point =>
                    point.x == interval.start + i &&
                    point.y == interval.otherCoord
                ) && [...entity.cells].some(point =>
                    point.x == interval.start + i &&
                    point.y == interval.otherCoord - 1
                ));
        }
        else if (i == interval.end - interval.start) {
            return (([...entity.cells].some(point =>    // Case 2
                point.x == interval.start + i + 1 &&
                point.y == interval.otherCoord
            )
                && ![...interval.entity.cells].some(point =>
                    point.x == interval.start + i + 1 &&
                    point.y == interval.otherCoord - 1
                )
            ) || ([...entity.cells].some(point =>   // Case 1
                point.x == interval.start + i + 1 &&
                point.y == interval.otherCoord
            )
                && [...entity.cells].some(point =>
                    point.x == interval.start + i + 1 &&
                    point.y == interval.otherCoord - 1
                )
            ))  // Same as internal
                && ([...entity.cells].some(point =>
                    point.x == interval.start + i &&
                    point.y == interval.otherCoord
                ) && [...entity.cells].some(point =>
                    point.x == interval.start + i &&
                    point.y == interval.otherCoord - 1
                ));
        }
        // Internal
        return [...entity.cells].some(point =>
            point.x == interval.start + i &&
            point.y == interval.otherCoord
        ) && [...entity.cells].some(point =>
            point.x == interval.start + i &&
            point.y == interval.otherCoord - 1
        );
    }

    if (interval.side == "bottom") {
        if (i == 0) {
            return (([...entity.cells].some(point =>    // Case 2
                point.x == interval.start + i - 1 &&
                point.y == interval.otherCoord
            )
                && ![...interval.entity.cells].some(point =>
                    point.x == interval.start + i - 1 &&
                    point.y == interval.otherCoord + 1
                )
            ) || ([...entity.cells].some(point =>   // Case 1
                point.x == interval.start + i - 1 &&
                point.y == interval.otherCoord
            )
                && [...entity.cells].some(point =>
                    point.x == interval.start + i - 1 &&
                    point.y == interval.otherCoord + 1
                )
            ))  // Same as internal
                && ([...entity.cells].some(point =>
                    point.x == interval.start + i &&
                    point.y == interval.otherCoord
                ) && [...entity.cells].some(point =>
                    point.x == interval.start + i &&
                    point.y == interval.otherCoord + 1
                ));
        }
        else if (i == interval.end - interval.start) {
            return (([...entity.cells].some(point =>    // Case 2
                point.x == interval.start + i + 1 &&
                point.y == interval.otherCoord
            )
                && ![...interval.entity.cells].some(point =>
                    point.x == interval.start + i + 1 &&
                    point.y == interval.otherCoord + 1
                )
            ) || ([...entity.cells].some(point =>   // Case 1
                point.x == interval.start + i + 1 &&
                point.y == interval.otherCoord
            )
                && [...entity.cells].some(point =>
                    point.x == interval.start + i + 1 &&
                    point.y == interval.otherCoord + 1
                )
            ))  // Same as internal
                && ([...entity.cells].some(point =>
                    point.x == interval.start + i &&
                    point.y == interval.otherCoord
                ) && [...entity.cells].some(point =>
                    point.x == interval.start + i &&
                    point.y == interval.otherCoord + 1
                ));
        }
        // Internal
        return [...entity.cells].some(point =>
            point.x == interval.start + i &&
            point.y == interval.otherCoord
        ) && [...entity.cells].some(point =>
            point.x == interval.start + i &&
            point.y == interval.otherCoord + 1
        );
    }

    if (interval.side == "left") {
        if (i == 0) {
            return (([...entity.cells].some(point =>    // Case 2
                point.y == interval.start + i - 1 &&
                point.x == interval.otherCoord
            )
                && ![...interval.entity.cells].some(point =>
                    point.y == interval.start + i - 1 &&
                    point.x == interval.otherCoord - 1
                )
            ) || ([...entity.cells].some(point =>   // Case 1
                point.y == interval.start + i - 1 &&
                point.x == interval.otherCoord
            )
                && [...entity.cells].some(point =>
                    point.y == interval.start + i - 1 &&
                    point.x == interval.otherCoord - 1
                )
            ))  // Same as internal
                && ([...entity.cells].some(point =>
                    point.y == interval.start + i &&
                    point.x == interval.otherCoord
                ) && [...entity.cells].some(point =>
                    point.y == interval.start + i &&
                    point.x == interval.otherCoord - 1
                ));
        }
        else if (i == interval.end - interval.start) {
            return (([...entity.cells].some(point =>    // Case 2
                point.y == interval.start + i + 1 &&
                point.x == interval.otherCoord
            )
                && ![...interval.entity.cells].some(point =>
                    point.y == interval.start + i + 1 &&
                    point.x == interval.otherCoord - 1
                )
            ) || ([...entity.cells].some(point =>   // Case 1
                point.y == interval.start + i + 1 &&
                point.x == interval.otherCoord
            )
                && [...entity.cells].some(point =>
                    point.y == interval.start + i + 1 &&
                    point.x == interval.otherCoord - 1
                )
            ))  // Same as internal
                && ([...entity.cells].some(point =>
                    point.y == interval.start + i &&
                    point.x == interval.otherCoord
                ) && [...entity.cells].some(point =>
                    point.y == interval.start + i &&
                    point.x == interval.otherCoord - 1
                ));
        }   
        // Internal
        return [...entity.cells].some(point =>
            point.y == interval.start + i &&
            point.x == interval.otherCoord
        ) && [...entity.cells].some(point =>
            point.y == interval.start + i &&
            point.x == interval.otherCoord - 1
        );
    }

    if (interval.side == "right") {
        if (i == 0) {
            return (([...entity.cells].some(point =>    // Case 2
                point.y == interval.start + i - 1 &&
                point.x == interval.otherCoord
            )
                && ![...interval.entity.cells].some(point =>
                    point.y == interval.start + i - 1 &&
                    point.x == interval.otherCoord + 1
                )
            ) || ([...entity.cells].some(point =>   // Case 1
                point.y == interval.start + i - 1 &&
                point.x == interval.otherCoord
            )
                && [...entity.cells].some(point =>
                    point.y == interval.start + i - 1 &&
                    point.x == interval.otherCoord + 1
                )
            ))  // Same as internal
                && ([...entity.cells].some(point =>
                    point.y == interval.start + i &&
                    point.x == interval.otherCoord
                ) && [...entity.cells].some(point =>
                    point.y == interval.start + i &&
                    point.x == interval.otherCoord + 1
                ));
        }
        else if (i == interval.end - interval.start) {
            return (([...entity.cells].some(point =>    // Case 2
                point.y == interval.start + i + 1 &&
                point.x == interval.otherCoord
            )
                && ![...interval.entity.cells].some(point =>
                    point.y == interval.start + i + 1 &&
                    point.x == interval.otherCoord - 1
                )
            ) || ([...entity.cells].some(point =>   // Case 1
                point.y == interval.start + i + 1 &&
                point.x == interval.otherCoord
            )
                && [...entity.cells].some(point =>
                    point.y == interval.start + i + 1 &&
                    point.x == interval.otherCoord - 1
                )
            ))  // Same as internal
                && ([...entity.cells].some(point =>
                    point.y == interval.start + i &&
                    point.x == interval.otherCoord
                ) && [...entity.cells].some(point =>
                    point.y == interval.start + i &&
                    point.x == interval.otherCoord + 1
                ));
        }
        // Internal
        return [...entity.cells].some(point =>
            point.y == interval.start + i &&
            point.x == interval.otherCoord
        ) && [...entity.cells].some(point =>
            point.y == interval.start + i &&
            point.x == interval.otherCoord + 1
        );
    }
}

function roundedPolygonPath(originalPoints, radius, removeLastPoint) {
    let points = originalPoints.slice();
    if (removeLastPoint) points.pop();
    const path = new Path2D();
    const len = points.length;

    if (len < 3) return path;

    let d = "";

    for (let i = 0; i < len; i++) {
        const prev = points[(i - 1 + len) % len];
        const curr = points[i];
        const next = points[(i + 1) % len];

        // Direction vectors
        const v1 = { x: curr.x - prev.x, y: curr.y - prev.y };
        const v2 = { x: next.x - curr.x, y: next.y - curr.y };
        const len1 = Math.hypot(v1.x, v1.y);
        const len2 = Math.hypot(v2.x, v2.y);
        v1.x /= len1; v1.y /= len1;
        v2.x /= len2; v2.y /= len2;

        // Compute interior angle
        const dot = v1.x * v2.x + v1.y * v2.y;
        const angle = Math.acos(Math.min(Math.max(dot, -1), 1));

        // Direction of turn (cross product sign)
        const cross = v1.x * v2.y - v1.y * v2.x;
        const isClockwise = cross < 0;

        // Compute distance from corner to tangent points
        const tanHalf = Math.tan(angle / 2);
        const offset = Math.min(radius / tanHalf, len1 / 2, len2 / 2);

        // Compute start and end tangent points
        const start = {
            x: curr.x - v1.x * offset,
            y: curr.y - v1.y * offset
        };
        const end = {
            x: curr.x + v2.x * offset,
            y: curr.y + v2.y * offset
        };

        if (i === 0) path.moveTo(start.x, start.y);
        else path.lineTo(start.x, start.y);

        // Draw inward arc (reversed direction!)
        path.arcTo(curr.x, curr.y, end.x, end.y, radius);

        if (i === 0) d += `M ${start.x},${start.y}`;
        else d += ` L ${start.x},${start.y}`;

        const sweep = isClockwise ? 0 : 1;
        d += ` A ${radius},${radius} 0 0 ${sweep} ${end.x},${end.y}`;
    }

    path.closePath();
    d += " Z";

    return [path, d];
}

function computeCacheKey(entities) {
    return entities.map(e => e.headers[0]).join('|');
}

function saveColorCache(cacheKey, assignment, colors, cost) {
    const cache = JSON.parse(localStorage.getItem('colorCache') || '{}');
    cache[cacheKey] = {
        colorAssignment: assignment,
        colors,
        cost
    };
    localStorage.setItem('colorCache', JSON.stringify(cache));
}

function loadColorCache(cacheKey) {
  const cache = JSON.parse(localStorage.getItem('colorCache') || '{}');
  return cache[cacheKey] || null;
}

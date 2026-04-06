// ============================================================
// Return Value Mutation targets (future)
// Covers: return true/false, return null, return 0/1, return ""
// Mutations: true↔false, return x → return null, etc.
// ============================================================

/**
 * Returns a boolean true.
 * Future target: return true → return false
 */
function alwaysTrue() {
    return true;
}

/**
 * Returns a boolean false.
 * Future target: return false → return true
 */
function alwaysFalse() {
    return false;
}

/**
 * Returns null.
 * Future target: return null → return undefined
 */
function returnsNull() {
    return null;
}

/**
 * Returns a computed numeric value.
 * Future target: return x → return 0
 */
function computeSum(arr) {
    let total = 0;
    for (let i = 0; i < arr.length; i++) {
        total += arr[i];
    }
    return total;
}

/**
 * Returns an empty string.
 * Future target: return "" → return "mutated"
 */
function emptyString() {
    return "";
}

/**
 * Returns an empty array.
 * Future target: return [] → return null
 */
function emptyArray() {
    return [];
}

/**
 * Returns an empty object.
 * Future target: return {} → return null
 */
function emptyObject() {
    return {};
}

/**
 * Returns 1 (used as a truthy numeric).
 * Future target: return 1 → return 0
 */
function unity() {
    return 1;
}

/**
 * Returns 0 (used as a falsy numeric).
 * Future target: return 0 → return 1
 */
function zero() {
    return 0;
}

/**
 * Conditional return with early exit.
 * Future target: remove early return / negate return
 */
function findElement(arr, target) {
    for (let i = 0; i < arr.length; i++) {
        if (arr[i] === target) {
            return i;
        }
    }
    return -1;
}

/**
 * Returns this (method chaining pattern).
 * Future target: return this → return null
 */
class Builder {
    constructor() {
        this.items = [];
    }

    addItem(item) {
        this.items.push(item);
        return this;
    }

    build() {
        return [...this.items];
    }
}

module.exports = {
    alwaysTrue,
    alwaysFalse,
    returnsNull,
    computeSum,
    emptyString,
    emptyArray,
    emptyObject,
    unity,
    zero,
    findElement,
    Builder
};

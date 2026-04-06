// ============================================================
// Relational Operator Replacement (ROR) targets
// Covers: <, <=, >, >=, ==, !=, ===, !==
// Mutations: > ↔ >=, < ↔ <=, == ↔ !=, === ↔ !==
// ============================================================

/**
 * Checks strict greater-than.
 * ROR target: > → >=
 */
function isGreaterThan(a, b) {
    return a > b;
}

/**
 * Checks greater-than-or-equal.
 * ROR target: >= → >
 */
function isGreaterThanOrEqual(a, b) {
    return a >= b;
}

/**
 * Checks strict less-than.
 * ROR target: < → <=
 */
function isLessThan(a, b) {
    return a < b;
}

/**
 * Checks less-than-or-equal.
 * ROR target: <= → <
 */
function isLessThanOrEqual(a, b) {
    return a <= b;
}

/**
 * Checks loose equality.
 * EQ_NEGATE target: == → !=
 */
function isLooseEqual(a, b) {
    return a == b;
}

/**
 * Checks loose inequality.
 * EQ_NEGATE target: != → ==
 */
function isLooseNotEqual(a, b) {
    return a != b;
}

/**
 * Checks strict equality.
 * EQ_NEGATE target: === → !==
 */
function isStrictEqual(a, b) {
    return a === b;
}

/**
 * Checks strict inequality.
 * EQ_NEGATE target: !== → ===
 */
function isStrictNotEqual(a, b) {
    return a !== b;
}

/**
 * Clamps a value between min and max using boundary comparisons.
 * ROR targets: < and >
 */
function clamp(value, min, max) {
    if (value < min) {
        return min;
    }
    if (value > max) {
        return max;
    }
    return value;
}

/**
 * Returns the grade letter based on a score.
 * ROR targets: >=
 */
function getGrade(score) {
    if (score >= 90) {
        return 'A';
    } else if (score >= 80) {
        return 'B';
    } else if (score >= 70) {
        return 'C';
    } else if (score >= 60) {
        return 'D';
    } else {
        return 'F';
    }
}

/**
 * Checks if value is within an inclusive range.
 * ROR targets: >= and <=
 */
function isInRange(value, min, max) {
    return value >= min && value <= max;
}

module.exports = {
    isGreaterThan,
    isGreaterThanOrEqual,
    isLessThan,
    isLessThanOrEqual,
    isLooseEqual,
    isLooseNotEqual,
    isStrictEqual,
    isStrictNotEqual,
    clamp,
    getGrade,
    isInRange
};

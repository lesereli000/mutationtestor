// ============================================================
// Unary Operator Mutation targets (future)
// Covers: -, +, !, typeof, void, delete
// Mutations: remove -, remove !, negate unary
// ============================================================

/**
 * Unary negation.
 * Future target: remove unary - (return x instead of -x)
 */
function negate(x) {
    return -x;
}

/**
 * Unary plus (type coercion to number).
 * Future target: remove unary +
 */
function toNumber(value) {
    return +value;
}

/**
 * Logical NOT.
 * Future target: remove ! operator
 */
function logicalNot(value) {
    return !value;
}

/**
 * typeof operator.
 * Future target: return constant string instead
 */
function getType(value) {
    return typeof value;
}

/**
 * void operator.
 * Future target: remove void
 */
function returnsUndefined() {
    return void 0;
}

/**
 * Double negation for boolean coercion.
 * Future target: remove one or both !
 */
function isTruthy(value) {
    return !!value;
}

/**
 * Unary minus in expression.
 * Future target: remove negation
 */
function reflectY(point) {
    return { x: point.x, y: -point.y };
}

/**
 * Combined unary and binary ops.
 * Targets: unary - and binary +
 */
function invertAndOffset(value, offset) {
    return -value + offset;
}

module.exports = {
    negate,
    toNumber,
    logicalNot,
    getType,
    returnsUndefined,
    isTruthy,
    reflectY,
    invertAndOffset
};

// ============================================================
// Bitwise Operator Mutation targets (future)
// Covers: &, |, ^, ~, <<, >>
// Mutations: & ↔ |, ^ ↔ &, << ↔ >>, ~ removal
// ============================================================

/**
 * Bitwise AND.
 * Future target: & → |
 */
function bitwiseAnd(a, b) {
    return a & b;
}

/**
 * Bitwise OR.
 * Future target: | → &
 */
function bitwiseOr(a, b) {
    return a | b;
}

/**
 * Bitwise XOR.
 * Future target: ^ → &
 */
function bitwiseXor(a, b) {
    return a ^ b;
}

/**
 * Bitwise NOT.
 * Future target: remove ~
 */
function bitwiseNot(a) {
    return ~a;
}

/**
 * Left shift.
 * Future target: << → >>
 */
function leftShift(value, positions) {
    return value << positions;
}

/**
 * Right shift.
 * Future target: >> → <<
 */
function rightShift(value, positions) {
    return value >> positions;
}

/**
 * Unsigned right shift.
 * Future target: >>> → <<
 */
function unsignedRightShift(value, positions) {
    return value >>> positions;
}

/**
 * Check if a specific bit is set using & mask.
 * Future target: & → |
 */
function isBitSet(value, bitPosition) {
    let mask = 1 << bitPosition;
    return (value & mask) !== 0;
}

/**
 * Set a specific bit using | mask.
 * Future target: | → &
 */
function setBit(value, bitPosition) {
    let mask = 1 << bitPosition;
    return value | mask;
}

/**
 * Toggle a bit using ^ mask.
 * Future target: ^ → &
 */
function toggleBit(value, bitPosition) {
    let mask = 1 << bitPosition;
    return value ^ mask;
}

/**
 * Extract RGB channels from a color integer using bitwise ops.
 * Multiple future targets: >>, &
 */
function extractRGB(color) {
    let r = (color >> 16) & 0xFF;
    let g = (color >> 8) & 0xFF;
    let b = color & 0xFF;
    return { r, g, b };
}

module.exports = {
    bitwiseAnd,
    bitwiseOr,
    bitwiseXor,
    bitwiseNot,
    leftShift,
    rightShift,
    unsignedRightShift,
    isBitSet,
    setBit,
    toggleBit,
    extractRGB
};

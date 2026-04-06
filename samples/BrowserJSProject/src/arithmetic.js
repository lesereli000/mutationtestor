// ============================================================
// Arithmetic Operator Replacement (AOR) targets
// Covers: +, -, *, /, %
// Mutations: + ↔ -, * ↔ /, % → *, and cross-swaps
// ============================================================

/**
 * Adds two numbers.
 * AOR target: + → -
 */
function add(a, b) {
    return a + b;
}

/**
 * Subtracts b from a.
 * AOR target: - → +
 */
function subtract(a, b) {
    return a - b;
}

/**
 * Multiplies two numbers.
 * AOR target: * → /
 */
function multiply(a, b) {
    return a * b;
}

/**
 * Divides a by b.
 * AOR target: / → *
 */
function divide(a, b) {
    if (b === 0) {
        return NaN;
    }
    return a / b;
}

/**
 * Returns remainder of a divided by b.
 * AOR target: % → *
 */
function modulo(a, b) {
    return a % b;
}

/**
 * Computes a compound expression with multiple operators.
 * AOR targets: + and * in same expression
 */
function linearEquation(m, x, b) {
    return m * x + b;
}

/**
 * Calculates the area of a trapezoid.
 * AOR targets: +, *, /
 */
function trapezoidArea(base1, base2, height) {
    return (base1 + base2) * height / 2;
}

/**
 * Computes distance between two 2D points.
 * AOR targets: -, *
 */
function distance(x1, y1, x2, y2) {
    let dx = x2 - x1;
    let dy = y2 - y1;
    return Math.sqrt(dx * dx + dy * dy);
}

module.exports = {
    add,
    subtract,
    multiply,
    divide,
    modulo,
    linearEquation,
    trapezoidArea,
    distance
};

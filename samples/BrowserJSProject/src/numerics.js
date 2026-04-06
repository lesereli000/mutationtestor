// ============================================================
// Numeric Literal Type (NLT) Mutation targets
// Covers: integer, float, exponential notation
// Mutations: integer ↔ float ↔ exponential
// ============================================================

/**
 * Uses integer literal 42.
 * NLT_INTEGER_TO_FLOAT target: 42 → 42.0
 * NLT_INTEGER_TO_EXPONENTIAL target: 42 → 4.2e1
 */
function getAnswerToLife() {
    let answer = 42;
    return answer;
}

/**
 * Uses integer literal in arithmetic.
 * NLT_INTEGER_TO_FLOAT target: 100 → 100.0
 */
function percentOf(value) {
    let divisor = 100;
    return value / divisor;
}

/**
 * Uses zero as edge case.
 * NLT_INTEGER_TO_FLOAT target: 0 → 0.0
 */
function getZero() {
    let zero = 0;
    return zero;
}

/**
 * Uses large integer.
 * NLT_INTEGER_TO_EXPONENTIAL target: 1000000 → 1.0e6
 */
function getMillion() {
    let million = 1000000;
    return million;
}

/**
 * Uses float literal 3.14.
 * NLT_FLOAT_TO_INTEGER target: 3.14 → 3
 * NLT_FLOAT_TO_EXPONENTIAL target: 3.14 → 3.14e0
 */
function circleArea(radius) {
    let pi = 3.14;
    return pi * radius * radius;
}

/**
 * Uses float literal 0.5.
 * NLT_FLOAT_TO_INTEGER target: 0.5 → 0
 */
function half(value) {
    let factor = 0.5;
    return value * factor;
}

/**
 * Uses float in temperature conversion.
 * NLT_FLOAT_TO_INTEGER targets: 1.8, 32.0
 */
function celsiusToFahrenheit(celsius) {
    let multiplier = 1.8;
    let offset = 32.0;
    return celsius * multiplier + offset;
}

/**
 * Uses exponential notation 1e3.
 * NLT_EXPONENTIAL_TO_INTEGER target: 1e3 → 1000
 * NLT_EXPONENTIAL_TO_FLOAT target: 1e3 → 1000.0
 */
function toKilo(value) {
    let kilo = 1e3;
    return value / kilo;
}

/**
 * Uses exponential 2.5e2.
 * NLT_EXPONENTIAL_TO_INTEGER target: 2.5e2 → 250
 */
function getQuarterThousand() {
    let value = 2.5e2;
    return value;
}

/**
 * Uses negative exponent 1e-3.
 * NLT_EXPONENTIAL_TO_FLOAT target
 */
function toMilli(value) {
    let milli = 1e-3;
    return value * milli;
}

/**
 * Mixed numeric literals in one function.
 * Multiple NLT targets
 */
function computePhysics(mass, velocity) {
    let half = 0.5;
    let gravity = 10;
    let kineticEnergy = half * mass * velocity * velocity;
    let potentialEnergy = mass * gravity * velocity;
    return kineticEnergy + potentialEnergy;
}

module.exports = {
    getAnswerToLife,
    percentOf,
    getZero,
    getMillion,
    circleArea,
    half,
    celsiusToFahrenheit,
    toKilo,
    getQuarterThousand,
    toMilli,
    computePhysics
};

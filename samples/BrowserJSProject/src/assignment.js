// ============================================================
// Assignment Operator Mutation targets (future)
// Covers: +=, -=, *=, /=, %=, **=
// Mutations: += ↔ -=, *= ↔ /=, etc.
// ============================================================

/**
 * Accumulates a running total using +=.
 * Future target: += → -=
 */
function accumulate(values) {
    let total = 0;
    for (let i = 0; i < values.length; i++) {
        total += values[i];
    }
    return total;
}

/**
 * Decrements a budget using -=.
 * Future target: -= → +=
 */
function spendBudget(budget, expenses) {
    let remaining = budget;
    for (let i = 0; i < expenses.length; i++) {
        remaining -= expenses[i];
    }
    return remaining;
}

/**
 * Scales values using *=.
 * Future target: *= → /=
 */
function scaleUp(value, factor, times) {
    let result = value;
    for (let i = 0; i < times; i++) {
        result *= factor;
    }
    return result;
}

/**
 * Reduces values using /=.
 * Future target: /= → *=
 */
function halveRepeatedly(value, times) {
    let result = value;
    for (let i = 0; i < times; i++) {
        result /= 2;
    }
    return result;
}

/**
 * Uses modulo assignment %=.
 * Future target: %= → *=
 */
function wrapAround(value, max) {
    let result = value;
    result %= max;
    return result;
}

/**
 * Uses exponentiation assignment **=.
 * Future target: **= → *=
 */
function powerUp(base, exponent) {
    let result = base;
    result **= exponent;
    return result;
}

/**
 * Mixed assignment operators in a single function.
 */
function processMetrics(initialValue, adjustments) {
    let result = initialValue;
    result += adjustments.add;
    result -= adjustments.subtract;
    result *= adjustments.multiply;
    result /= adjustments.divide;
    return result;
}

module.exports = {
    accumulate,
    spendBudget,
    scaleUp,
    halveRepeatedly,
    wrapAround,
    powerUp,
    processMetrics
};

// ============================================================
// Ternary / Conditional Expression Mutation targets (future)
// Covers: condition ? a : b
// Mutations: swap branches, negate condition, return constant
// ============================================================

/**
 * Simple ternary with numeric return.
 * Future target: swap branches (a ? b : c → a ? c : b)
 */
function absoluteValue(x) {
    return x >= 0 ? x : -x;
}

/**
 * Ternary with string return.
 * Future target: negate condition
 */
function parity(n) {
    return n % 2 === 0 ? 'even' : 'odd';
}

/**
 * Nested ternary.
 * Future target: swap inner/outer branches
 */
function sign(x) {
    return x > 0 ? 1 : (x < 0 ? -1 : 0);
}

/**
 * Ternary used for default values.
 * Future target: swap branches
 */
function getDisplayName(user) {
    return user.nickname ? user.nickname : user.fullName;
}

/**
 * Ternary with function calls.
 * Future target: negate condition, swap branches
 */
function feeCalculation(age, basePrice) {
    let discount = age < 18 ? 0.5 : 1.0;
    return basePrice * discount;
}

/**
 * Ternary in template literal (browser-style string building).
 * Future target: swap branches
 */
function formatStatus(isActive) {
    return `Status: ${isActive ? 'Active' : 'Inactive'}`;
}

/**
 * Conditional with boolean result.
 * Future target: return true → return false
 */
function isEligible(age, hasLicense) {
    return age >= 16 && hasLicense ? true : false;
}

module.exports = {
    absoluteValue,
    parity,
    sign,
    getDisplayName,
    feeCalculation,
    formatStatus,
    isEligible
};

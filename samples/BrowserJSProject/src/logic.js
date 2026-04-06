// ============================================================
// Logical Operator Replacement targets
// Covers: &&, ||, !
// Mutations: && ↔ ||, ! removal/insertion (future)
// ============================================================

/**
 * Logical AND: both conditions must be true.
 * LOGICAL_AND_TO_OR target: && → ||
 */
function bothPositive(a, b) {
    return a > 0 && b > 0;
}

/**
 * Logical OR: at least one condition true.
 * LOGICAL_OR_TO_AND target: || → &&
 */
function eitherPositive(a, b) {
    return a > 0 || b > 0;
}

/**
 * Logical NOT negation.
 * Future target: remove ! (negate condition removal)
 */
function isNotZero(value) {
    return !isZero(value);
}

/**
 * Helper for negation test.
 */
function isZero(value) {
    return value === 0;
}

/**
 * Compound AND with three conditions.
 * LOGICAL_AND_TO_OR target: multiple && operators
 */
function isValidTriangle(a, b, c) {
    return a + b > c && a + c > b && b + c > a;
}

/**
 * Compound OR with multiple conditions.
 * LOGICAL_OR_TO_AND target: multiple || operators
 */
function isWeekend(day) {
    return day === 'Saturday' || day === 'Sunday';
}

/**
 * Mixed logical operators.
 * Targets: both && and || in same expression
 */
function canAccessContent(age, hasPermission, isAdmin) {
    return (age >= 18 && hasPermission) || isAdmin;
}

/**
 * Nested logical with NOT.
 * Targets: &&, ||, !
 */
function shouldSendNotification(isEnabled, isQuietHours, isUrgent) {
    return isEnabled && (!isQuietHours || isUrgent);
}

/**
 * Short-circuit evaluation showcase.
 * LOGICAL_AND_TO_OR target
 */
function safeGet(obj, key) {
    return obj !== null && obj !== undefined && obj[key] !== undefined;
}

/**
 * Double negation (future: !! removal).
 */
function toBool(value) {
    return !!value;
}

module.exports = {
    bothPositive,
    eitherPositive,
    isNotZero,
    isZero,
    isValidTriangle,
    isWeekend,
    canAccessContent,
    shouldSendNotification,
    safeGet,
    toBool
};

const {
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
} = require('../src/logic');

describe('Logical AND to OR', () => {

    test('bothPositive returns true when both positive', () => {
        expect(bothPositive(1, 2)).toBe(true);
    });

    test('bothPositive returns false when one is negative', () => {
        // Catches && → || (would return true if mutated)
        expect(bothPositive(-1, 2)).toBe(false);
        expect(bothPositive(1, -2)).toBe(false);
    });

    test('bothPositive returns false when both negative', () => {
        expect(bothPositive(-1, -2)).toBe(false);
    });
});

describe('Logical OR to AND', () => {

    test('eitherPositive returns true when one positive', () => {
        // Catches || → && (would return false if mutated)
        expect(eitherPositive(-1, 2)).toBe(true);
        expect(eitherPositive(1, -2)).toBe(true);
    });

    test('eitherPositive returns false when both negative', () => {
        expect(eitherPositive(-1, -2)).toBe(false);
    });

    test('eitherPositive returns true when both positive', () => {
        expect(eitherPositive(1, 2)).toBe(true);
    });
});

describe('Logical NOT', () => {

    test('isNotZero returns true for non-zero', () => {
        expect(isNotZero(5)).toBe(true);
    });

    test('isNotZero returns false for zero', () => {
        expect(isNotZero(0)).toBe(false);
    });

    test('isZero returns true for zero', () => {
        expect(isZero(0)).toBe(true);
    });

    test('isZero returns false for non-zero', () => {
        expect(isZero(1)).toBe(false);
    });
});

describe('Compound logical expressions', () => {

    test('isValidTriangle for valid triangle', () => {
        expect(isValidTriangle(3, 4, 5)).toBe(true);
    });

    test('isValidTriangle for invalid triangle', () => {
        // Catches && → || in compound expression
        expect(isValidTriangle(1, 2, 10)).toBe(false);
    });

    test('isValidTriangle for degenerate triangle', () => {
        expect(isValidTriangle(1, 2, 3)).toBe(false);
    });

    test('isWeekend returns true for Saturday', () => {
        expect(isWeekend('Saturday')).toBe(true);
    });

    test('isWeekend returns true for Sunday', () => {
        expect(isWeekend('Sunday')).toBe(true);
    });

    test('isWeekend returns false for Monday', () => {
        // Catches || → && (would return false for both days if mutated)
        expect(isWeekend('Monday')).toBe(false);
    });
});

describe('Mixed logical operators', () => {

    test('canAccessContent with valid age and permission', () => {
        expect(canAccessContent(20, true, false)).toBe(true);
    });

    test('canAccessContent with admin override', () => {
        expect(canAccessContent(10, false, true)).toBe(true);
    });

    test('canAccessContent denied for minor without permission', () => {
        expect(canAccessContent(10, false, false)).toBe(false);
    });

    test('canAccessContent denied for adult without permission', () => {
        // Catches && → || (middle condition matters)
        expect(canAccessContent(20, false, false)).toBe(false);
    });

    test('shouldSendNotification when enabled and not quiet', () => {
        expect(shouldSendNotification(true, false, false)).toBe(true);
    });

    test('shouldSendNotification blocked during quiet hours', () => {
        expect(shouldSendNotification(true, true, false)).toBe(false);
    });

    test('shouldSendNotification urgent overrides quiet hours', () => {
        expect(shouldSendNotification(true, true, true)).toBe(true);
    });

    test('shouldSendNotification disabled blocks all', () => {
        expect(shouldSendNotification(false, false, true)).toBe(false);
    });
});

describe('Short-circuit and coercion', () => {

    test('safeGet returns true for valid key', () => {
        expect(safeGet({ a: 1 }, 'a')).toBe(true);
    });

    test('safeGet returns false for null object', () => {
        expect(safeGet(null, 'a')).toBe(false);
    });

    test('safeGet returns false for undefined object', () => {
        expect(safeGet(undefined, 'a')).toBe(false);
    });

    test('safeGet returns false for missing key', () => {
        expect(safeGet({ a: 1 }, 'b')).toBe(false);
    });

    test('toBool coerces truthy values', () => {
        expect(toBool(1)).toBe(true);
        expect(toBool('hello')).toBe(true);
    });

    test('toBool coerces falsy values', () => {
        expect(toBool(0)).toBe(false);
        expect(toBool('')).toBe(false);
        expect(toBool(null)).toBe(false);
    });
});

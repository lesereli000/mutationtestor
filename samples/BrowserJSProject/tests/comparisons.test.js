const {
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
} = require('../src/comparisons');

describe('Relational Operator Replacement (ROR)', () => {

    // --- Greater than ---
    test('isGreaterThan returns true when a > b', () => {
        expect(isGreaterThan(5, 3)).toBe(true);
    });

    test('isGreaterThan returns false when equal', () => {
        // This catches > → >= mutation
        expect(isGreaterThan(5, 5)).toBe(false);
    });

    test('isGreaterThan returns false when a < b', () => {
        expect(isGreaterThan(3, 5)).toBe(false);
    });

    // --- Greater than or equal ---
    test('isGreaterThanOrEqual returns true when equal', () => {
        expect(isGreaterThanOrEqual(5, 5)).toBe(true);
    });

    test('isGreaterThanOrEqual returns false when less', () => {
        // This catches >= → > mutation
        expect(isGreaterThanOrEqual(4, 5)).toBe(false);
    });

    // --- Less than ---
    test('isLessThan returns true when a < b', () => {
        expect(isLessThan(2, 7)).toBe(true);
    });

    test('isLessThan returns false when equal', () => {
        // This catches < → <= mutation
        expect(isLessThan(5, 5)).toBe(false);
    });

    // --- Less than or equal ---
    test('isLessThanOrEqual returns true when equal', () => {
        expect(isLessThanOrEqual(5, 5)).toBe(true);
    });

    test('isLessThanOrEqual returns false when greater', () => {
        // This catches <= → < mutation
        expect(isLessThanOrEqual(6, 5)).toBe(false);
    });
});

describe('Equality Operator Negation (EQ_NEGATE)', () => {

    // --- Loose equality ---
    test('isLooseEqual returns true for same values', () => {
        expect(isLooseEqual(5, 5)).toBe(true);
    });

    test('isLooseEqual returns true for coercible values', () => {
        expect(isLooseEqual(5, '5')).toBe(true);
    });

    test('isLooseEqual returns false for different values', () => {
        expect(isLooseEqual(5, 6)).toBe(false);
    });

    // --- Loose inequality ---
    test('isLooseNotEqual returns true for different values', () => {
        expect(isLooseNotEqual(5, 6)).toBe(true);
    });

    test('isLooseNotEqual returns false for same values', () => {
        expect(isLooseNotEqual(5, 5)).toBe(false);
    });

    // --- Strict equality ---
    test('isStrictEqual returns true for identical values', () => {
        expect(isStrictEqual(5, 5)).toBe(true);
    });

    test('isStrictEqual returns false for different types', () => {
        expect(isStrictEqual(5, '5')).toBe(false);
    });

    // --- Strict inequality ---
    test('isStrictNotEqual returns true for different types', () => {
        expect(isStrictNotEqual(5, '5')).toBe(true);
    });

    test('isStrictNotEqual returns false for identical values', () => {
        expect(isStrictNotEqual(5, 5)).toBe(false);
    });
});

describe('Comparison in context', () => {

    test('clamp returns min when below range', () => {
        expect(clamp(-5, 0, 10)).toBe(0);
    });

    test('clamp returns max when above range', () => {
        expect(clamp(15, 0, 10)).toBe(10);
    });

    test('clamp returns value when in range', () => {
        expect(clamp(5, 0, 10)).toBe(5);
    });

    test('clamp returns boundary values correctly', () => {
        // Catches < → <= and > → >= at boundaries
        expect(clamp(0, 0, 10)).toBe(0);
        expect(clamp(10, 0, 10)).toBe(10);
    });

    test('getGrade returns correct grades at boundaries', () => {
        expect(getGrade(90)).toBe('A');
        expect(getGrade(89)).toBe('B');
        expect(getGrade(80)).toBe('B');
        expect(getGrade(79)).toBe('C');
        expect(getGrade(70)).toBe('C');
        expect(getGrade(69)).toBe('D');
        expect(getGrade(60)).toBe('D');
        expect(getGrade(59)).toBe('F');
    });

    test('isInRange returns true for value in range', () => {
        expect(isInRange(5, 1, 10)).toBe(true);
    });

    test('isInRange returns true at boundaries', () => {
        expect(isInRange(1, 1, 10)).toBe(true);
        expect(isInRange(10, 1, 10)).toBe(true);
    });

    test('isInRange returns false outside range', () => {
        expect(isInRange(0, 1, 10)).toBe(false);
        expect(isInRange(11, 1, 10)).toBe(false);
    });
});

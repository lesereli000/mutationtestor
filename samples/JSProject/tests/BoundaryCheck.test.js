const {
    clampToZero,
    isUnderLimit
} = require('../src/BoundaryCheck');

describe('BoundaryCheck Tests', () => {

    test('clampToZero returns 0 for negative numbers', () => {
        expect(clampToZero(-5)).toBe(0);
        expect(clampToZero(-1)).toBe(0);
    });

    test('clampToZero returns 0 when n is 0', () => {
        expect(clampToZero(0)).toBe(0);
    });

    test('clampToZero returns positive numbers unchanged', () => {
        expect(clampToZero(5)).toBe(5);
        expect(clampToZero(100)).toBe(100);
    });

    test('isUnderLimit returns true when x is under limit', () => {
        expect(isUnderLimit(3, 10)).toBe(true);
        expect(isUnderLimit(0, 5)).toBe(true);
    });

    test('isUnderLimit returns true when x equals limit', () => {
        expect(isUnderLimit(10, 10)).toBe(true);
    });

    test('isUnderLimit returns false when x exceeds limit', () => {
        expect(isUnderLimit(11, 10)).toBe(false);
        expect(isUnderLimit(100, 50)).toBe(false);
    });
});
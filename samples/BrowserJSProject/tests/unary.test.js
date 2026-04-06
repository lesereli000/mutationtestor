const {
    negate,
    toNumber,
    logicalNot,
    getType,
    returnsUndefined,
    isTruthy,
    reflectY,
    invertAndOffset
} = require('../src/unary');

describe('Unary Operator Mutations (future)', () => {

    test('negate returns negative of positive', () => {
        expect(negate(5)).toBe(-5);
    });

    test('negate returns positive of negative', () => {
        expect(negate(-3)).toBe(3);
    });

    test('negate of zero is zero', () => {
        expect(negate(0)).toBe(-0);
    });

    test('toNumber coerces string to number', () => {
        expect(toNumber('42')).toBe(42);
        expect(toNumber('3.14')).toBeCloseTo(3.14);
    });

    test('toNumber coerces boolean to number', () => {
        expect(toNumber(true)).toBe(1);
        expect(toNumber(false)).toBe(0);
    });

    test('logicalNot inverts true', () => {
        expect(logicalNot(true)).toBe(false);
    });

    test('logicalNot inverts false', () => {
        expect(logicalNot(false)).toBe(true);
    });

    test('logicalNot inverts truthy/falsy', () => {
        expect(logicalNot(1)).toBe(false);
        expect(logicalNot(0)).toBe(true);
    });

    test('getType identifies types correctly', () => {
        expect(getType(42)).toBe('number');
        expect(getType('hello')).toBe('string');
        expect(getType(true)).toBe('boolean');
        expect(getType(undefined)).toBe('undefined');
        expect(getType(null)).toBe('object');
    });

    test('returnsUndefined returns undefined', () => {
        expect(returnsUndefined()).toBeUndefined();
    });

    test('isTruthy identifies truthy values', () => {
        expect(isTruthy(1)).toBe(true);
        expect(isTruthy('hi')).toBe(true);
        expect(isTruthy([])).toBe(true);
    });

    test('isTruthy identifies falsy values', () => {
        expect(isTruthy(0)).toBe(false);
        expect(isTruthy('')).toBe(false);
        expect(isTruthy(null)).toBe(false);
        expect(isTruthy(undefined)).toBe(false);
    });

    test('reflectY negates y coordinate', () => {
        expect(reflectY({ x: 3, y: 5 })).toEqual({ x: 3, y: -5 });
        expect(reflectY({ x: 0, y: -2 })).toEqual({ x: 0, y: 2 });
    });

    test('invertAndOffset computes -value + offset', () => {
        expect(invertAndOffset(3, 10)).toBe(7);    // -3 + 10
        expect(invertAndOffset(-5, 2)).toBe(7);    // 5 + 2
        expect(invertAndOffset(0, 0)).toBe(0);
    });
});

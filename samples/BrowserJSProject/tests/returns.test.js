const {
    alwaysTrue,
    alwaysFalse,
    returnsNull,
    computeSum,
    emptyString,
    emptyArray,
    emptyObject,
    unity,
    zero,
    findElement,
    Builder
} = require('../src/returns');

describe('Return Value Mutations (future)', () => {

    test('alwaysTrue returns true', () => {
        expect(alwaysTrue()).toBe(true);
    });

    test('alwaysFalse returns false', () => {
        expect(alwaysFalse()).toBe(false);
    });

    test('returnsNull returns null', () => {
        expect(returnsNull()).toBeNull();
    });

    test('computeSum returns correct total', () => {
        expect(computeSum([1, 2, 3])).toBe(6);
    });

    test('computeSum returns 0 for empty array', () => {
        expect(computeSum([])).toBe(0);
    });

    test('computeSum would fail if returned 0 unconditionally', () => {
        expect(computeSum([10])).toBe(10);
    });

    test('emptyString returns empty string', () => {
        expect(emptyString()).toBe('');
        expect(emptyString()).toHaveLength(0);
    });

    test('emptyArray returns empty array', () => {
        expect(emptyArray()).toEqual([]);
        expect(emptyArray()).toHaveLength(0);
    });

    test('emptyObject returns empty object', () => {
        expect(emptyObject()).toEqual({});
    });

    test('unity returns 1', () => {
        expect(unity()).toBe(1);
        expect(unity()).not.toBe(0);
    });

    test('zero returns 0', () => {
        expect(zero()).toBe(0);
        expect(zero()).not.toBe(1);
    });

    test('findElement returns correct index', () => {
        expect(findElement([10, 20, 30], 20)).toBe(1);
    });

    test('findElement returns -1 when not found', () => {
        expect(findElement([10, 20, 30], 99)).toBe(-1);
    });

    test('findElement returns first occurrence', () => {
        expect(findElement([5, 5, 5], 5)).toBe(0);
    });

    test('Builder chains and builds', () => {
        const result = new Builder().addItem('a').addItem('b').build();
        expect(result).toEqual(['a', 'b']);
    });

    test('Builder addItem returns this for chaining', () => {
        const builder = new Builder();
        expect(builder.addItem('x')).toBe(builder);
    });
});

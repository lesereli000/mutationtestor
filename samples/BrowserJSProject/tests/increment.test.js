const {
    countUp,
    countDown,
    preIncrement,
    preDecrement,
    sumRange,
    findFirstAbove,
    adjustBalance
} = require('../src/increment');

describe('Increment Removal (REMOVE_INCREMENT)', () => {

    test('countUp increments correctly', () => {
        expect(countUp(0, 5)).toBe(5);
        expect(countUp(10, 3)).toBe(13);
    });

    test('countUp with zero steps stays at start', () => {
        expect(countUp(5, 0)).toBe(5);
    });

    test('countDown decrements correctly', () => {
        expect(countDown(10, 3)).toBe(7);
        expect(countDown(0, 5)).toBe(-5);
    });

    test('countDown with zero steps stays at start', () => {
        expect(countDown(5, 0)).toBe(5);
    });

    test('preIncrement returns value + 1', () => {
        expect(preIncrement(5)).toBe(6);
        expect(preIncrement(-1)).toBe(0);
    });

    test('preDecrement returns value - 1', () => {
        expect(preDecrement(5)).toBe(4);
        expect(preDecrement(0)).toBe(-1);
    });
});

describe('Increment in loops', () => {

    test('sumRange sums 1 to 10', () => {
        expect(sumRange(1, 10)).toBe(55);
    });

    test('sumRange with single element', () => {
        expect(sumRange(5, 5)).toBe(5);
    });

    test('findFirstAbove returns correct index', () => {
        expect(findFirstAbove([1, 3, 5, 7, 9], 4)).toBe(2);
    });

    test('findFirstAbove returns first index when first element qualifies', () => {
        expect(findFirstAbove([10, 1, 2], 5)).toBe(0);
    });

    test('findFirstAbove returns -1 when nothing found', () => {
        expect(findFirstAbove([1, 2, 3], 100)).toBe(-1);
    });

    test('findFirstAbove returns -1 for empty array', () => {
        expect(findFirstAbove([], 0)).toBe(-1);
    });
});

describe('Mixed increment and decrement', () => {

    test('adjustBalance processes deposits and withdrawals', () => {
        const result = adjustBalance(1000, 3, 2);
        // 1000 + 3*100 - 2*50 = 1000 + 300 - 100 = 1200
        expect(result.balance).toBe(1200);
        expect(result.transactions).toBe(5);
    });

    test('adjustBalance with no transactions', () => {
        const result = adjustBalance(500, 0, 0);
        expect(result.balance).toBe(500);
        expect(result.transactions).toBe(0);
    });

    test('adjustBalance with only deposits', () => {
        const result = adjustBalance(0, 5, 0);
        expect(result.balance).toBe(500);
        expect(result.transactions).toBe(5);
    });
});

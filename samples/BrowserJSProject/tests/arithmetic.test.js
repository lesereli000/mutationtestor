const {
    add,
    subtract,
    multiply,
    divide,
    modulo,
    linearEquation,
    trapezoidArea,
    distance
} = require('../src/arithmetic');

describe('Arithmetic Operations (AOR)', () => {

    // --- Addition ---
    test('add returns sum of two positive numbers', () => {
        expect(add(3, 4)).toBe(7);
    });

    test('add returns sum with zero', () => {
        expect(add(5, 0)).toBe(5);
    });

    test('add returns sum of negatives', () => {
        expect(add(-2, -3)).toBe(-5);
    });

    test('add with mixed signs', () => {
        expect(add(-3, 5)).toBe(2);
    });

    // --- Subtraction ---
    test('subtract returns difference', () => {
        expect(subtract(10, 3)).toBe(7);
    });

    test('subtract returns negative when b > a', () => {
        expect(subtract(3, 10)).toBe(-7);
    });

    test('subtract with zero', () => {
        expect(subtract(5, 0)).toBe(5);
    });

    // --- Multiplication ---
    test('multiply returns product', () => {
        expect(multiply(6, 7)).toBe(42);
    });

    test('multiply by zero returns zero', () => {
        expect(multiply(100, 0)).toBe(0);
    });

    test('multiply negatives returns positive', () => {
        expect(multiply(-3, -4)).toBe(12);
    });

    // --- Division ---
    test('divide returns quotient', () => {
        expect(divide(20, 4)).toBe(5);
    });

    test('divide by zero returns NaN', () => {
        expect(divide(10, 0)).toBeNaN();
    });

    test('divide returns decimal', () => {
        expect(divide(7, 2)).toBe(3.5);
    });

    // --- Modulo ---
    test('modulo returns remainder', () => {
        expect(modulo(17, 5)).toBe(2);
    });

    test('modulo returns 0 for evenly divisible', () => {
        expect(modulo(10, 5)).toBe(0);
    });

    // --- Compound expressions ---
    test('linearEquation computes y = mx + b', () => {
        expect(linearEquation(2, 3, 1)).toBe(7);   // 2*3 + 1
        expect(linearEquation(0, 5, 3)).toBe(3);   // 0*5 + 3
    });

    test('trapezoidArea computes (b1+b2)*h/2', () => {
        expect(trapezoidArea(3, 5, 4)).toBe(16);   // (3+5)*4/2
        expect(trapezoidArea(10, 10, 5)).toBe(50);
    });

    test('distance computes Euclidean distance', () => {
        expect(distance(0, 0, 3, 4)).toBe(5);
        expect(distance(1, 1, 1, 1)).toBe(0);
        expect(distance(0, 0, 1, 0)).toBe(1);
    });
});

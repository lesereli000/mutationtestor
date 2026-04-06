const {
    getAnswerToLife,
    percentOf,
    getZero,
    getMillion,
    circleArea,
    half,
    celsiusToFahrenheit,
    toKilo,
    getQuarterThousand,
    toMilli,
    computePhysics
} = require('../src/numerics');

describe('NLT Integer Literals', () => {

    test('getAnswerToLife returns 42', () => {
        expect(getAnswerToLife()).toBe(42);
    });

    test('percentOf divides by 100', () => {
        expect(percentOf(50)).toBe(0.5);
        expect(percentOf(100)).toBe(1);
    });

    test('getZero returns 0', () => {
        expect(getZero()).toBe(0);
    });

    test('getMillion returns 1000000', () => {
        expect(getMillion()).toBe(1000000);
    });
});

describe('NLT Float Literals', () => {

    test('circleArea uses pi = 3.14', () => {
        expect(circleArea(1)).toBeCloseTo(3.14, 2);
        expect(circleArea(5)).toBeCloseTo(78.5, 1);
    });

    test('circleArea would fail if 3.14 truncated to 3', () => {
        // NLT_FLOAT_TO_INTEGER: 3.14 → 3 would give 75 instead of 78.5
        expect(circleArea(5)).not.toBe(75);
    });

    test('half returns half the value', () => {
        expect(half(10)).toBe(5);
        expect(half(7)).toBe(3.5);
    });

    test('half would fail if 0.5 truncated to 0', () => {
        // NLT_FLOAT_TO_INTEGER: 0.5 → 0 would give 0
        expect(half(10)).not.toBe(0);
    });

    test('celsiusToFahrenheit converts correctly', () => {
        expect(celsiusToFahrenheit(0)).toBeCloseTo(32, 1);
        expect(celsiusToFahrenheit(100)).toBeCloseTo(212, 1);
    });
});

describe('NLT Exponential Literals', () => {

    test('toKilo divides by 1000', () => {
        expect(toKilo(5000)).toBe(5);
        expect(toKilo(1)).toBe(0.001);
    });

    test('getQuarterThousand returns 250', () => {
        expect(getQuarterThousand()).toBe(250);
    });

    test('toMilli multiplies by 0.001', () => {
        expect(toMilli(1000)).toBe(1);
        expect(toMilli(5)).toBeCloseTo(0.005, 6);
    });
});

describe('Mixed numeric types', () => {

    test('computePhysics calculates correctly', () => {
        // KE = 0.5 * 2 * 3^2 = 9
        // PE = 2 * 10 * 3 = 60
        // Total = 69
        expect(computePhysics(2, 3)).toBe(69);
    });

    test('computePhysics with zero velocity', () => {
        expect(computePhysics(5, 0)).toBe(0);
    });
});

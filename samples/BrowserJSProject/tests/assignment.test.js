const {
    accumulate,
    spendBudget,
    scaleUp,
    halveRepeatedly,
    wrapAround,
    powerUp,
    processMetrics
} = require('../src/assignment');

describe('Assignment Operator Mutations (future)', () => {

    test('accumulate sums array with +=', () => {
        expect(accumulate([1, 2, 3, 4, 5])).toBe(15);
        expect(accumulate([])).toBe(0);
    });

    test('accumulate with negatives', () => {
        expect(accumulate([-1, -2, -3])).toBe(-6);
    });

    test('spendBudget subtracts expenses with -=', () => {
        expect(spendBudget(1000, [100, 200, 300])).toBe(400);
    });

    test('spendBudget with no expenses', () => {
        expect(spendBudget(500, [])).toBe(500);
    });

    test('scaleUp multiplies repeatedly with *=', () => {
        expect(scaleUp(1, 2, 3)).toBe(8);   // 1 * 2 * 2 * 2
        expect(scaleUp(5, 3, 2)).toBe(45);  // 5 * 3 * 3
    });

    test('halveRepeatedly divides with /=', () => {
        expect(halveRepeatedly(100, 2)).toBe(25);  // 100 / 2 / 2
        expect(halveRepeatedly(64, 3)).toBe(8);    // 64 / 2 / 2 / 2
    });

    test('wrapAround uses %=', () => {
        expect(wrapAround(17, 5)).toBe(2);
        expect(wrapAround(10, 10)).toBe(0);
    });

    test('powerUp uses **=', () => {
        expect(powerUp(2, 3)).toBe(8);
        expect(powerUp(3, 2)).toBe(9);
    });

    test('processMetrics chains assignment operators', () => {
        const result = processMetrics(100, {
            add: 50,       // 100 + 50 = 150
            subtract: 30,  // 150 - 30 = 120
            multiply: 2,   // 120 * 2 = 240
            divide: 4      // 240 / 4 = 60
        });
        expect(result).toBe(60);
    });
});

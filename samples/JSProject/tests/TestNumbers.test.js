const { testNumbers } = require('../src/TestNumbers');

describe('Number type tests', () => {
    test('testNumbers should calculate correctly', () => {
        // 42 + 3.14 + 10000000000 = 10000000045.14
        expect(testNumbers()).toBeCloseTo(10000000045.14, 2);
    });
});
const { 
    calculatePi, 
    halfValue, 
    precisionTest, 
    negativeFloat,
    smallDecimal 
} = require('../src/floatTypes');

describe('Float Type Tests', () => {
    
    test('calculatePi should return pi approximation', () => {
        expect(calculatePi()).toBeCloseTo(3.14159, 5);
    });
    
    test('halfValue should multiply by 0.5', () => {
        expect(halfValue(10)).toBe(5);
        expect(halfValue(7)).toBe(3.5);
        expect(halfValue(0)).toBe(0);
    });
    
    test('precisionTest shows floating point precision', () => {
        const result = precisionTest();
        expect(result).toBeCloseTo(0.3, 1);
    });
    
    test('negativeFloat should return -2.5', () => {
        expect(negativeFloat()).toBe(-2.5);
    });
    
    test('smallDecimal should return 0.001', () => {
        expect(smallDecimal()).toBe(0.001);
    });
});
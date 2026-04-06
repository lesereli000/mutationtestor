const { 
    largeNumber, 
    smallNumber, 
    scientificCalculation,
    negativeExponent,
    largeExponent 
} = require('../src/exponentialTypes');

describe('Exponential Type Tests', () => {
    
    test('largeNumber should return 10 billion', () => {
        expect(largeNumber()).toBe(10000000000);
    });
    
    test('smallNumber should return 0.00001', () => {
        expect(smallNumber()).toBe(0.00001);
    });
    
    test('scientificCalculation should multiply by 2500', () => {
        expect(scientificCalculation(4)).toBe(10000);
        expect(scientificCalculation(1)).toBe(2500);
        expect(scientificCalculation(0)).toBe(0);
    });
    
    test('negativeExponent should return 0.005', () => {
        expect(negativeExponent()).toBe(0.005);
    });
    
    test('largeExponent should return 1e20', () => {
        expect(largeExponent()).toBe(1e20);
    });
});
const { 
    addInteger, 
    multiplyInteger, 
    zeroInteger, 
    negativeInteger,
    largeInteger 
} = require('../src/integerTypes');

describe('Integer Type Tests', () => {
    
    test('addInteger should add 42', () => {
        expect(addInteger(8)).toBe(50);
        expect(addInteger(0)).toBe(42);
        expect(addInteger(-42)).toBe(0);
    });
    
    test('multiplyInteger should multiply by 10', () => {
        expect(multiplyInteger(5)).toBe(50);
        expect(multiplyInteger(0)).toBe(0);
        expect(multiplyInteger(-3)).toBe(-30);
    });
    
    test('zeroInteger should return 0', () => {
        expect(zeroInteger()).toBe(0);
    });
    
    test('negativeInteger should return -42', () => {
        expect(negativeInteger()).toBe(-42);
    });
    
    test('largeInteger should return 1000', () => {
        expect(largeInteger()).toBe(1000);
    });
});
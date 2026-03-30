const { 
    complexCalculation, 
    typeConversion,
    edgeCases 
} = require('../src/mixedTypes');

describe('Mixed Type Tests', () => {
    
    test('complexCalculation combines all types', () => {
        // (10 + 0.5) * 100 * 2 = 2100
        expect(complexCalculation(2)).toBe(2100);
        
        // (10 + 0.5) * 100 * 1 = 1050
        expect(complexCalculation(1)).toBe(1050);
        
        // (10 + 0.5) * 100 * 0 = 0
        expect(complexCalculation(0)).toBe(0);
    });
    
    test('typeConversion uses all three types', () => {
        // 42 * 1.5 * 10 = 630
        expect(typeConversion()).toBe(630);
    });
    
    test('edgeCases handles zero, tiny, and huge numbers', () => {
        // 0 + 0.0001 + 1000000000000000 ≈ 1000000000000000
        expect(edgeCases()).toBeCloseTo(1e15, -10);
    });
});
const {
    absoluteValue,
    parity,
    sign,
    getDisplayName,
    feeCalculation,
    formatStatus,
    isEligible
} = require('../src/ternary');

describe('Ternary / Conditional Mutations (future)', () => {

    test('absoluteValue returns positive for negative input', () => {
        expect(absoluteValue(-7)).toBe(7);
    });

    test('absoluteValue returns same for positive input', () => {
        expect(absoluteValue(5)).toBe(5);
    });

    test('absoluteValue returns 0 for zero', () => {
        // Catches branch swap at boundary
        expect(absoluteValue(0)).toBe(0);
    });

    test('parity returns even for even numbers', () => {
        expect(parity(4)).toBe('even');
        expect(parity(0)).toBe('even');
    });

    test('parity returns odd for odd numbers', () => {
        expect(parity(3)).toBe('odd');
        expect(parity(-1)).toBe('odd');
    });

    test('sign returns 1 for positive', () => {
        expect(sign(10)).toBe(1);
    });

    test('sign returns -1 for negative', () => {
        expect(sign(-10)).toBe(-1);
    });

    test('sign returns 0 for zero', () => {
        expect(sign(0)).toBe(0);
    });

    test('getDisplayName prefers nickname', () => {
        expect(getDisplayName({ nickname: 'Al', fullName: 'Albert' })).toBe('Al');
    });

    test('getDisplayName falls back to fullName', () => {
        expect(getDisplayName({ nickname: '', fullName: 'Albert' })).toBe('Albert');
    });

    test('feeCalculation gives half price for minors', () => {
        expect(feeCalculation(10, 100)).toBe(50);
    });

    test('feeCalculation gives full price for adults', () => {
        expect(feeCalculation(25, 100)).toBe(100);
    });

    test('feeCalculation at age boundary', () => {
        // Catches negate condition at 18
        expect(feeCalculation(18, 100)).toBe(100);
        expect(feeCalculation(17, 100)).toBe(50);
    });

    test('formatStatus shows Active', () => {
        expect(formatStatus(true)).toBe('Status: Active');
    });

    test('formatStatus shows Inactive', () => {
        expect(formatStatus(false)).toBe('Status: Inactive');
    });

    test('isEligible returns true when conditions met', () => {
        expect(isEligible(16, true)).toBe(true);
    });

    test('isEligible returns false when underage', () => {
        expect(isEligible(15, true)).toBe(false);
    });

    test('isEligible returns false without license', () => {
        expect(isEligible(20, false)).toBe(false);
    });
});

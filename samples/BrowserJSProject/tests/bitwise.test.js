const {
    bitwiseAnd,
    bitwiseOr,
    bitwiseXor,
    bitwiseNot,
    leftShift,
    rightShift,
    unsignedRightShift,
    isBitSet,
    setBit,
    toggleBit,
    extractRGB
} = require('../src/bitwise');

describe('Bitwise Operator Mutations (future)', () => {

    test('bitwiseAnd performs AND', () => {
        expect(bitwiseAnd(0b1100, 0b1010)).toBe(0b1000);  // 8
        expect(bitwiseAnd(0xFF, 0x0F)).toBe(0x0F);
    });

    test('bitwiseOr performs OR', () => {
        expect(bitwiseOr(0b1100, 0b1010)).toBe(0b1110);  // 14
        expect(bitwiseOr(0xF0, 0x0F)).toBe(0xFF);
    });

    test('bitwiseXor performs XOR', () => {
        expect(bitwiseXor(0b1100, 0b1010)).toBe(0b0110);  // 6
        expect(bitwiseXor(0xFF, 0xFF)).toBe(0);
    });

    test('bitwiseNot performs NOT', () => {
        expect(bitwiseNot(0)).toBe(-1);
        expect(bitwiseNot(-1)).toBe(0);
        expect(bitwiseNot(5)).toBe(-6);
    });

    test('leftShift shifts bits left', () => {
        expect(leftShift(1, 3)).toBe(8);
        expect(leftShift(5, 1)).toBe(10);
    });

    test('rightShift shifts bits right', () => {
        expect(rightShift(8, 3)).toBe(1);
        expect(rightShift(10, 1)).toBe(5);
    });

    test('unsignedRightShift shifts without sign extension', () => {
        expect(unsignedRightShift(8, 2)).toBe(2);
        expect(unsignedRightShift(-1, 28)).toBe(15);
    });

    test('isBitSet checks specific bits', () => {
        expect(isBitSet(0b1010, 1)).toBe(true);
        expect(isBitSet(0b1010, 0)).toBe(false);
        expect(isBitSet(0b1010, 3)).toBe(true);
    });

    test('setBit sets specific bits', () => {
        expect(setBit(0b0000, 2)).toBe(0b0100);
        expect(setBit(0b1010, 0)).toBe(0b1011);
    });

    test('toggleBit flips specific bits', () => {
        expect(toggleBit(0b1010, 1)).toBe(0b1000);
        expect(toggleBit(0b1010, 0)).toBe(0b1011);
    });

    test('extractRGB decomposes color correctly', () => {
        const rgb = extractRGB(0xFF8040);
        expect(rgb.r).toBe(255);
        expect(rgb.g).toBe(128);
        expect(rgb.b).toBe(64);
    });

    test('extractRGB handles black', () => {
        const rgb = extractRGB(0x000000);
        expect(rgb.r).toBe(0);
        expect(rgb.g).toBe(0);
        expect(rgb.b).toBe(0);
    });

    test('extractRGB handles white', () => {
        const rgb = extractRGB(0xFFFFFF);
        expect(rgb.r).toBe(255);
        expect(rgb.g).toBe(255);
        expect(rgb.b).toBe(255);
    });
});

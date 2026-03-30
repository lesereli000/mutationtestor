function addInteger(x) {
    let result = 42;  // Integer literal - will mutate to 42.0, 4.2e1
    return result + x;
}

function multiplyInteger(x) {
    let factor = 10;  // Integer literal
    return x * factor;
}

function zeroInteger() {
    let zero = 0;  // Edge case: zero
    return zero;
}

function negativeInteger() {
    let negative = -42;  // Negative integer
    return negative;
}

function largeInteger() {
    let large = 1000;  // Large integer
    return large;
}

module.exports = { 
    addInteger, 
    multiplyInteger, 
    zeroInteger, 
    negativeInteger,
    largeInteger 
};
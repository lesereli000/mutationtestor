function isPositive(x) {
    return x > 0;
}

function isInRange(x, min, max) {
    return x >= min && x <= max;
}

function isZero(x) {
    return x === 0;
}

function isValid(x) {
    if (x > 0 && x < 100) {
        return true;
    }
    return false;
}

function clampToZero(n) {
    if (n <= 0) n = 0;   // should NOT mutate - equivalent
    return n;
}

function isUnderLimit(x, limit) {
    if (x <= limit) {    // should mutate - real logic
        return true;
    }
    return false;
}

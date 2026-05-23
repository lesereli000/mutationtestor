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


function incrementCounter(n) {
    let count = n;
    count++;
    return count;
}

function decrementCounter(n) {
    let count = n;
    count--;
    return count;
}

function absoluteValue(n) {
    if (n < 0) {
        return -n;
    }
    return n;
}

function logAndReturn(x) {
    console.log(x);
    return x;
}

function sumUpTo(n) {
    let total = 0;
    for (let i = 0; i < n; i++) {
        total += i;
    }
    return total;
}

function countDown(n) {
    let count = n;
    while (count > 0) {
        count--;
    }
    return count;
}
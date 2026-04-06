// ============================================================
// Increment / Decrement Mutation targets
// Covers: ++, --
// Mutations: remove ++/--, swap ++ ↔ -- (future)
// ============================================================

/**
 * Post-increment counter.
 * REMOVE_INCREMENT target: remove counter++
 */
function countUp(start, steps) {
    let counter = start;
    for (let i = 0; i < steps; i++) {
        counter++;
    }
    return counter;
}

/**
 * Post-decrement counter.
 * REMOVE_INCREMENT target: remove counter--
 */
function countDown(start, steps) {
    let counter = start;
    for (let i = 0; i < steps; i++) {
        counter--;
    }
    return counter;
}

/**
 * Pre-increment.
 * REMOVE_INCREMENT target: remove ++value
 */
function preIncrement(value) {
    return ++value;
}

/**
 * Pre-decrement.
 * REMOVE_INCREMENT target: remove --value
 */
function preDecrement(value) {
    return --value;
}

/**
 * Multiple increments in a loop (for-loop i++).
 * REMOVE_INCREMENT target: remove i++
 */
function sumRange(start, end) {
    let sum = 0;
    for (let i = start; i <= end; i++) {
        sum += i;
    }
    return sum;
}

/**
 * Increment used in while loop condition.
 * REMOVE_INCREMENT target
 */
function findFirstAbove(arr, threshold) {
    let index = 0;
    while (index < arr.length) {
        if (arr[index] > threshold) {
            return index;
        }
        index++;
    }
    return -1;
}

/**
 * Both increment and decrement in same function.
 * REMOVE_INCREMENT targets: both ++ and --
 * Future INC_TO_DEC / DEC_TO_INC target
 */
function adjustBalance(balance, deposits, withdrawals) {
    let count = 0;
    for (let i = 0; i < deposits; i++) {
        balance += 100;
        count++;
    }
    for (let i = 0; i < withdrawals; i++) {
        balance -= 50;
        count++;
    }
    return { balance, transactions: count };
}

module.exports = {
    countUp,
    countDown,
    preIncrement,
    preDecrement,
    sumRange,
    findFirstAbove,
    adjustBalance
};

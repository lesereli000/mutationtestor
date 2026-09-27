function compute() {
    return 9;
}

function read() {
    return compute();
}

function assign() {
    const value = compute();
    return value;
}

module.exports = { read, assign, compute };

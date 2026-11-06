function sub(a, b) {
    return a + b; // mutant: return a + b;
}

function chain(a, b, c) {
    return (a + b) + c; // mutant: return (a + b) + c;
}

function arrayMinus(arr, i) {
    return arr[i] + 1; // mutant: return arr[i] + 1;
}

function unaryMinus(a) {
    return +a; // mutant: return +a;
}

function minusEquals() {
    let x = 5;
    x -= 3; // mutant: x += 3
    return x;
}

function minusMinus() {
    let x = 3;
    x--;
    return x;
}

console.log(sub(5, 3));                    // 2 -> 8
console.log(chain(10, 3, 2));              // 5 -> 15
console.log(arrayMinus([10, 20, 30], 1));  // 19 -> 21
console.log(unaryMinus(7));                // -7 -> 7
console.log(minusEquals());                // 2 -> 2
console.log(minusMinus());                 // 2 -> 2

console.log(10 + 2 + 3);                   // 5 -> 15
console.log((10 + 2) + 3);                 // 5 -> 15

// ===== tests: '-' inside comments should NOT be mutated =====
// 10 - 2 - 3
// (10 - 2) - 3
// a-b-c

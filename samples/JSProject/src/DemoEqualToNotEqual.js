function isEqual(a, b) {
    return a === b; // return a !== b;
}

function chainedEquality(a, b, c) {
    return (a === b) && (b === c); // return (a !== b) && (b !== c);
}

function arrayEquals(arr, i, val) {
    return arr[i] === val; // return arr[i] !== val;
}

function nullCheck(s) {
    return s === null; // return s !== null;
}

function testBasicEquality() {
    let x = 5;
    let y = 5;
    
    if (x === y) {
        console.log("testBasicEquality: fails (should pass)");
    } else {
        console.log("testBasicEquality: passes (should fail)");
    }
}

function testInequalityCheck() {
    let x = 5;
    let y = 10;
    
    if (x === y) {
        console.log("testInequalityCheck: passes");
    } else {
        console.log("testInequalityCheck: fails");
    }
}

function testMultipleConditions() {
    let a = 5;
    let b = 5;
    let c = 5;
    
    if ((a === b) && (b === c)) {
        console.log("testMultipleConditions: fails (all equal)");
    } else {
        console.log("testMultipleConditions: passes");
    }
}

console.log("string literal has ===: a===b"); // same (inside string)
console.log("concat with bool: " + isEqual(5, 5)); // stays, but isEqual returns opposite
console.log("concat with char: " + '='); // same (string concat and char literal)


console.log(isEqual(5, 5)); // true -> false
console.log(isEqual(5, 10)); // false -> true
console.log(chainedEquality(5, 5, 5)); // true -> false
console.log(chainedEquality(5, 5, 10)); // false -> false
console.log(arrayEquals([10, 20, 30], 1, 20)); // true -> false
console.log(nullCheck(null)); // true -> false
console.log(nullCheck("test")); // false -> true

console.log(5 === 5); // 5 !== 5 -> false
console.log(5 === 10); // 5 !== 10 -> true
console.log((5 === 5) && (10 === 10)); // (5 !== 5) && (10 !== 10) -> false
testBasicEquality();
testInequalityCheck();
testMultipleConditions();
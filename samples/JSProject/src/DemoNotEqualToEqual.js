function isNotEqual(a, b) {
    return a !== b; // return a === b;
}

function chainedInequality(a, b, c) {
    return (a !== b) && (b !== c); // return (a === b) && (b === c);
}

function arrayNotEquals(arr, i, val) {
    return arr[i] !== val; // return arr[i] === val;
}

function notNullCheck(s) {
    return s !== null; // return s === null;
}

function testBasicInequality() {
    let x = 5;
    let y = 10;
    
    if (x !== y) {
        console.log("testBasicInequality: fails (should pass)");
    } else {
        console.log("testBasicInequality: passes (should fail)");
    }
}

function testEqualityCheck() {
    let x = 5;
    let y = 5;
    
    if (x !== y) {
        console.log("testEqualityCheck: passes");
    } else {
        console.log("testEqualityCheck: fails");
    }
}

function testMultipleConditions() {
    let a = 5;
    let b = 10;
    let c = 15;
    
    if ((a !== b) && (b !== c)) {
        console.log("testMultipleConditions: fails (all different)");
    } else {
        console.log("testMultipleConditions: passes");
    }
}

console.log("string literal has !==: a!==b"); // same (inside string)
console.log("concat with bool: " + isNotEqual(5, 10)); // stays, but isNotEqual returns opposite


console.log(isNotEqual(5, 10)); //  true -> false
console.log(isNotEqual(5, 5)); // false -> true
console.log(chainedInequality(5, 10, 15)); // true -> false
console.log(chainedInequality(5, 5, 10)); // false -> false
console.log(arrayNotEquals([10, 20, 30], 1, 25)); // true -> false
console.log(notNullCheck(null)); // false -> true
console.log(notNullCheck("test")); // true -> false

console.log(5 !== 10); // 5 === 10 -> false
console.log(5 !== 5); // 5 === 5 -> true
console.log((5 !== 10) && (10 !== 15)); // (5 === 10) && (10 === 15) -> false

testBasicInequality();
testEqualityCheck();
testMultipleConditions();
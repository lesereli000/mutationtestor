function complexCalculation(x) {
    let integerPart = 10;      // Integer
    let floatPart = 0.5;       // Float
    let multiplier = 1e2;      // Exponential (100)
    
    return (integerPart + floatPart) * multiplier * x;
}

function typeConversion() {
    let start = 42;            // Integer
    let factor = 1.5;          // Float
    let scale = 1e1;           // Exponential (10)
    
    return start * factor * scale;
}

function edgeCases() {
    let zero = 0;              // Integer zero
    let tiny = 0.0001;         // Small float
    let big = 1e15;            // Large exponential
    
    return zero + tiny + big;
}

module.exports = { 
    complexCalculation, 
    typeConversion,
    edgeCases 
};
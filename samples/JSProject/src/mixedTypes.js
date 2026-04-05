function complexCalculation(x) {
    let integerPart = 10;
    let floatPart = 0.5;
    let multiplier = 1e2;
    
    return (integerPart + floatPart) * multiplier * x;
}

function typeConversion() {
    let start = 42;
    let factor = 1.5;
    let scale = 1e1;
    
    return start * factor * scale;
}

function edgeCases() {
    let zero = 0;
    let tiny = 0.0001;
    let big = 1e15;
    
    return zero + tiny + big;
}
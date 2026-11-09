function add(a, b) {
    return a + b;
}

function GTtoGE(){
    a = 5;
    equal = a;
    if(a > equal){
        console.log("GT to GE Passes");
    } else {
        console.log("GT to GE Fails");
    }
}

function GEtoGT(){
    a = 5;
    equal = a;
    if(a >= equal){
        console.log("GE to GT Fails");
    } else {
        console.log("GE to GT Passes");
    }
}

console.log(add(2,3));

GTtoGE();
GEtoGT();
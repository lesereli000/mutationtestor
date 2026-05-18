const { add1 } = require("./Add1");
const { add2 } = require("./Add2");

function main() {
    const a = 5;
    const b = add1(a);
    const c = add2(b);
    const d = add2(c);
    const e = add1(add2(add1(d)));
    console.log(e);
}


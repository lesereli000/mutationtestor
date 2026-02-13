const assert = require("assert");
const { add1 } = require("../src/Add1");
const { add2 } = require("../src/Add2");

describe("TestAdd", function () {
    it("testAdd1", function () {
        assert.strictEqual(add1(5), 6);
        assert.strictEqual(add1(-1), 0);
        assert.strictEqual(add1(788), 789);
        assert.strictEqual(add1(11), 12);
        assert.strictEqual(add1(Number.MAX_SAFE_INTEGER - 1), Number.MAX_SAFE_INTEGER);
    });

    it("testAdd2", function () {
        assert.strictEqual(add2(5), 7);
        assert.strictEqual(add2(-1), 1);
        assert.strictEqual(add2(788), 790);
        assert.strictEqual(add2(11), 13);
        assert.strictEqual(add2(Number.MAX_SAFE_INTEGER - 2), Number.MAX_SAFE_INTEGER);
    });
});

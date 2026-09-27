const assert = require("assert");
const { assign, read } = require("../src/NonVoidCallDemo");

describe("NonVoidCallDemo", function () {
    it("testRead", function () {
        assert.strictEqual(read(), 9);
    });

    it("testAssign", function () {
        assert.strictEqual(assign(), 9);
    });
});

const assert = require("assert");
const { create } = require("../src/ConstructorCallDemo");

describe("ConstructorCallDemo", function () {
    it("testCreate", function () {
        const widget = create();
        assert.ok(widget);
        assert.strictEqual(widget.name, "demo");
    });
});

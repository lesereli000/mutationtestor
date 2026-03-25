const { add1 } = require('../src/Add1');
const assert = require('assert');

describe('Add1 Tests', function() {
    it('should add 1 to a number', function() {
        assert.strictEqual(add1(5), 6);
        assert.strictEqual(add1(0), 1);
        assert.strictEqual(add1(-1), 0);
    });
});
function Widget(name) {
    this.name = name;
}

function create() {
    return new Widget("demo");
}

module.exports = { Widget, create };

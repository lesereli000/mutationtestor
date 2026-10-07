const { Dog, Box, AllSyntaxDemo, JSSpecifics } = require('../src/prototypeSample');

describe('prototypeSample coverage', () => {
    test('run main', () => {
        AllSyntaxDemo.main();
    });

    test('static methods', () => {
        AllSyntaxDemo.staticMethod();
    });

    test('instance methods', () => {
        const demo = new AllSyntaxDemo();
        expect(demo.sum(1, 2, 3)).toBe(6);
    });

    test('other classes', () => {
        const dog = new Dog("Pug");
        expect(dog.breed).toBe("Pug");
        
        const box = new Box(1);
        expect(box.value).toBe(1);
    });
});

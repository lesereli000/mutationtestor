// prototypeSample.js

const Status = Object.freeze({ Started: "Started", Running: "Running", Finished: "Finished" })

class Animal {
    makeSound() { throw new Error("abstract") }
}

class Dog extends Animal {
    static dogCount = 0
    breed
    energy

    constructor(breed) {
        super()
        this.breed = breed
        Dog.dogCount++
        this.energy = 100
    }

    makeSound() { console.log("Woof!") }
}

class Box {
    value
    constructor(value) { this.value = value }
}

class ConsoleGreeter {
    greet(name) { console.log("Hello " + name) }
}

class AllSyntaxDemo {
    static counter = 0
    max = 10

    static staticMethod() { console.log("Static called") }

    sum(...nums) {
        let result = 0
        for (const n of nums) result += n
        return result
    }

    static main() {
        let a = 5, b = 10
        const flag = true
        const c = 'X'
        const msg = "Hello"

        // ================= OPERATORS (REAL) =================
        const add = a + b
        const sub = a - b
        const mul = a * b
        const div = Math.trunc(a / b)
        const mod = a % b

        a++
        b--

        const eq = (a === b)
        const neq = (a !== b)
        const gt = (a > b)
        const lt = (a < b)
        const ge = (a >= b)
        const le = (a <= b)

        const and = (a > 0 && b > 0)
        const or = (a > 0 || b > 0)
        const not = !and

        const bitAnd = a & b
        const bitOr = a | b
        const bitXor = a ^ b
        const bitNot = ~a
        const shl = a << 1
        const shr = a >> 1

        // ================= OPERATORS IN STRINGS =================
        const operatorString = "+ - * / % ++ -- === !== > < >= <= && || ! & | ^ ~ << >>"

        // ================= OPERATORS IN COMMENTS =================
        // + - * / % ++ --
        // === !== > < >= <=
        // && || !
        // & | ^ ~ << >>

        const ternary = (a > b) ? a : b

        // Loops
        for (let i = 0; i < 3; i++) {
            if (i === 1) continue
            console.log("For: " + i)
        }

        let j = 0
        while (j < 2) console.log("While: " + (j++))

        let k = 0
        do {
            console.log("Do-While: " + (k++))
        } while (k < 2)

        // Switch
        const status = Status.Running
        switch (status) {
            case Status.Started: console.log("Started"); break
            case Status.Running: console.log("Running"); break
            case Status.Finished: console.log("Finished"); break
            default: console.log("Unknown"); break
        }

        // Collections & "LINQ-like"
        const list = ["A", "B"]
        list.forEach(e => console.log("Lambda: " + e))
        const doubled = list.map(s => s + s)

        // Object creation & inheritance
        const dog = new Dog("Labrador")
        dog.makeSound()

        // "Interface" usage
        const greeter = new ConsoleGreeter()
        greeter.greet("World")

        // "Generics" demo via Box
        const box = new Box(42)
        console.log(box.value)

        // Type casting
        const d = 3.5
        const di = d | 0
        console.log(di)

        // Null check
        let s = null
        if (s === null) console.log("s is null")

        // synchronized-ish counter (single-threaded demo)
        AllSyntaxDemo.counter++

        console.log({
            flag, c, msg,
            add, sub, mul, div, mod,
            eq, neq, gt, lt, ge, le,
            and, or, not,
            bitAnd, bitOr, bitXor, bitNot, shl, shr,
            operatorString,
            ternary,
            doubled,
            dogCount: Dog.dogCount,
            max: new AllSyntaxDemo().max,
            counter: AllSyntaxDemo.counter
        })
    }
}

AllSyntaxDemo.main()
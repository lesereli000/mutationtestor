using System;
using System.Collections.Generic;
using System.Linq;

enum Status { Started, Running, Finished }

interface IGreeter
{
    void Greet(string name);
}

abstract class Animal
{
    public abstract void MakeSound();
}

class Dog : Animal
{
    public string Breed { get; }
    public static int DogCount = 0;
    public volatile int Energy;

    public Dog(string breed)
    {
        Breed = breed;
        DogCount++;
        Energy = 100;
    }

    public override void MakeSound() => Console.WriteLine("Woof!");
}

class Box<T>
{
    public T Value;
    public Box(T value) { Value = value; }
}

class AllSyntaxDemo
{
    static int counter = 0;
    readonly int max = 10;

    static void StaticMethod() => Console.WriteLine("Static called");

    int Sum(params int[] nums)
    {
        int result = 0;
        foreach (int n in nums) result += n;
        return result;
    }

    static void Main()
    {
        int a = 5, b = 10;
        bool flag = true;
        char c = 'X';
        string msg = "Hello";

        // ================= OPERATORS (REAL) =================
        int add = a + b;
        int sub = a - b;
        int mul = a * b;
        int div = a / b;
        int mod = a % b;

        a++;
        b--;

        bool eq = (a == b);
        bool neq = (a != b);
        bool gt = (a > b);
        bool lt = (a < b);
        bool ge = (a >= b);
        bool le = (a <= b);

        bool and = (a > 0 && b > 0);
        bool or = (a > 0 || b > 0);
        bool not = !and;

        int bitAnd = a & b;
        int bitOr = a | b;
        int bitXor = a ^ b;
        int bitNot = ~a;
        int shl = a << 1;
        int shr = a >> 1;

        // ================= OPERATORS IN STRINGS =================
        string operatorString = "+ - * / % ++ -- == != > < >= <= && || ! & | ^ ~ << >>";

        // ================= OPERATORS IN COMMENTS =================
        // + - * / % ++ --
        // == != > < >= <=
        // && || !
        // & | ^ ~ << >>

        int ternary = (a > b) ? a : b;

        // Loops
        for (int i = 0; i < 3; i++)
        {
            if (i == 1) continue;
            Console.WriteLine("For: " + i);
        }

        int j = 0;
        while (j < 2) Console.WriteLine("While: " + j++);

        int k = 0;
        do
        {
            Console.WriteLine("Do-While: " + k++);
        } while (k < 2);

        // Switch
        Status status = Status.Running;
        switch (status)
        {
            case Status.Started: Console.WriteLine("Started"); break;
            case Status.Running: Console.WriteLine("Running"); break;
            case Status.Finished: Console.WriteLine("Finished"); break;
        }

        // Collections & LINQ
        List<string> list = new List<string> { "A", "B" };
        list.ForEach(e => Console.WriteLine("Lambda: " + e));
        var doubled = list.Select(s => s + s);

        // Object creation & inheritance
        Dog dog = new Dog("Labrador");
        dog.MakeSound();

        // Interface anonymous class via lambda
        IGreeter greeter = new ConsoleGreeter();
        greeter.Greet("World");

        // Generics
        Box<int> box = new Box<int>(42);
        Console.WriteLine(box.Value);

        // Type casting
        double d = 3.5;
        int di = (int)d;
        Console.WriteLine(di);

        // Null check
        string s = null;
        if (s == null) Console.WriteLine("s is null");

        // lock (synchronized)
        lock (typeof(AllSyntaxDemo)) counter++;
    }
}

class ConsoleGreeter : IGreeter
{
    public void Greet(string name) => Console.WriteLine("Hello " + name);
}

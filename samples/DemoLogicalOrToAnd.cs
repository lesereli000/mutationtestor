using System;

public class DemoLogicalOrToAnd {
    public static bool EitherPositive(int a, int b)
    {
        // should mutate here
        return a > 0 || b > 0;
    }

    public static void Main(string[] args)
    {
        Console.WriteLine($"Either positive? {EitherPositive(2, -3)}");
        // should not mutate in this string: "x || y"
    }
}

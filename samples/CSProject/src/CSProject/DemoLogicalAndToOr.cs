using System;

public class DemoLogicalAndToOr {
    public static bool BothPositive(int a, int b)
    {
        // should mutate here
        return a > 0 && b > 0;
    }

    public static void Main(string[] args)
    {
        Console.WriteLine($"Both positive? {BothPositive(2, 3)}");
        // should not mutate in this string: "x && y"
    }
}

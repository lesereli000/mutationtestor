using samples.CSProject.src;

namespace samples.CSProject.src;

public class Program
{
    public static void Main(string[] args)
    {
        int a = 5;
        int b = Add1.add1(a);
        int c = Add2.add2(b);
        int d = Add2.add2(c);
        int e = Add1.add1(Add2.add2(Add1.add1(d)));
        System.Console.WriteLine(e);
    }
}

namespace samples.CSProject.src
{
    public class Program
    {
        public static int RunPipeline(int x)
        {
            return Add1.add1(x) + Add2.add2(x) + Add3.add3(x);
        }

        public static int RunMinusPipeline(int x)
        {
            return Minus1.minus1(x)
                 + Minus2.minus2(x)
                 + Minus3.minus3(x);
        }

        public static int RunEqualityPipeline(int x)
        {
            return EqualsDemo.isEqual(x)
                 + NotEqualsDemo.isNotEqual(x);
        }

        public static void Main(string[] args)
        {
            System.Console.WriteLine("Pipeline: " + RunPipeline(1));

            System.Console.WriteLine("MinusPipeline: " + RunMinusPipeline(5));

            System.Console.WriteLine("EqualityPipeline: " + RunEqualityPipeline(5));

            System.Console.WriteLine("UseInt: " + TypeMutationDemo.UseInt());
            System.Console.WriteLine("UseDouble: " + TypeMutationDemo.UseDouble());
            System.Console.WriteLine("UseFloat: " + TypeMutationDemo.UseFloat());
        }
    }
}







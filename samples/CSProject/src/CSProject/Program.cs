namespace samples.CSProject.src
{
    public class Program
    {
        public static int RunPipeline(int x)
        {
            return Add1.add1(x) + Add2.add2(x) + Add3.add3(x);
        }

        public static void Main(string[] args)
        {
            System.Console.WriteLine("Pipeline: " + RunPipeline(1));
            System.Console.WriteLine("UseInt: " + TypeMutationDemo.UseInt());
            System.Console.WriteLine("UseDouble: " + TypeMutationDemo.UseDouble());
            System.Console.WriteLine("UseFloat: " + TypeMutationDemo.UseFloat());
        }
    }
}
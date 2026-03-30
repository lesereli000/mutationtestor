using samples.CSProject.src;

namespace samples.CSProject.src
{
    public class Program
    {
        public static float Choose(int x)
        {
            return x + 0.25f;
        }

        public static float Choose(double x)
        {
            return (float)x + 1.25f;
        }

        public static float Choose(float x)
        {
            return x + 2.25f;
        }

        public static float RunPipeline(float input)
        {
            int a = (int)input;
            int b = Add1.add1((int)a);
            double c = (int)Add2.add2((int)b);
            return Choose(a) + Choose(b) + Choose(c);
        }

        public static void Main(string[] args)
        {
            System.Console.WriteLine(RunPipeline(5));
        }
    }
}
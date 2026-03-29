using samples.CSProject.src;

namespace samples.CSProject.src
{
    public class Program
    {
        public static float Adjust(float x)
        {
            return x + 0.5f;
        }

        public static float Adjust(int x)
        {
            return x + 1.5f;
        }

        public static float RunPipeline(float input)
        {
            int a = (int)input;
            int b = Add1.add1(a);
            double c = Add2.add2(b);
            float d = (int)Add3.add3((float)c);
            return Adjust(d);
        }

        public static void Main(string[] args)
        {
            System.Console.WriteLine(RunPipeline(5));
        }
    }
}






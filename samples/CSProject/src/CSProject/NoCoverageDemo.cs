namespace samples.CSProject.src
{
    public static class NoCoverageDemo
    {
        public static int CoveredByTests(int x)
        {
            return x + 1;
        }

        public static int NotCoveredByTests(int x)
        {
            return x + 10;
        }
    }
}

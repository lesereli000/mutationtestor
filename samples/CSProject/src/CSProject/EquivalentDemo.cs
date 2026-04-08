namespace samples.CSProject.src
{
    public static class EquivalentDemo
    {
        public static int EquivalentOrToAnd(int x)
        {
            return x | x;
        }

        public static int NotEquivalentOrToAnd(int x)
        {
            return x | 1;
        }
    }
}
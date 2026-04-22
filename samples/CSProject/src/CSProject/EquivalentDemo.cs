namespace samples.CSProject.src
{
    public static class EquivalentDemo
    {
        public static int EquivalentOrToAnd(int x)
        {
            int i = 1;
            int j = 2;
            return x | x;
        }

        public static int NotEquivalentOrToAnd(int x)
        {
            return x | 1;
        }
    }
}
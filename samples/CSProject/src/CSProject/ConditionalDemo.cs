namespace CSProject
{
    public static class ConditionalDemo
    {
        public static int Killed(int x)
        {
            if (x > 100)
            {
                return 1;
            }
            return 0;
        }

        public static int Survived(int x)
        {
            if (x > 100)
            {
                return 1;
            }
            return 0;
        }
    }
}
namespace samples.CSProject.src
{
    public static class BoundaryCheck
    {
        public static int ClampToZero(int n)
        {
            if (n <= 0) n = 0;
            return n;
        }

        public static bool IsUnderLimit(int x, int limit)
        {
            if (x <= limit)
            {
                return true;
            }
            return false;
        }
    }
}

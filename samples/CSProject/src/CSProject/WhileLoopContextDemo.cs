namespace samples.CSProject.src
{
    public static class WhileLoopContextDemo
    {
        public static int NormalizeNegativeToZero(int n)
        {
            // Context-sensitive MSL skips mutating '<' to '<=' here.
            // If the loop became 'while (n <= 0)', input 0 would never exit.
            while (n < 0)
            {
                n = 0;
            }

            return n;
        }

        public static int NormalizePositiveToZero(int n)
        {
            // Context-sensitive MSL skips mutating '>' to '>=' here.
            // If the loop became 'while (n >= 0)', input 0 would never exit.
            while (n > 0)
            {
                n = 0;
            }

            return n;
        }

        public static bool IsWithinLimit(int n, int limit)
        {
            // This comparison is safe to mutate, so this file still appears
            // in the mutation report while the dangerous while loops are skipped.
            return n <= limit;
        }
    }
}

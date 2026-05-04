using System.Collections.Generic;

namespace CSProject
{
    public static class StatementMutationDemo
    {
        public static int Killed()
        {
            var values = new List<int>();
            values.Add(7);
            return values.Count;
        }

        public static int Survived()
        {
            var values = new List<int>();
            values.Add(7);
            return 1;
        }
    }
}

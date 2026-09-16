using System.Collections.Generic;

namespace samples.CSProject.src
{
    public sealed class PitEmptyReturnsDemo
    {
        public string KilledReturnsString()
        {
            return "items";
        }

        public int KilledReturnsInt()
        {
            return 7;
        }

        public List<int> KilledReturnsList()
        {
            return new List<int> { 1, 2, 3 };
        }

        public IEnumerable<string> KilledReturnsEnumerable()
        {
            return new[] { "alpha", "beta" };
        }

        public string SurvivedReturnsString()
        {
            return "items";
        }

        public List<int> SurvivedReturnsList()
        {
            return new List<int> { 1, 2, 3 };
        }
    }
}

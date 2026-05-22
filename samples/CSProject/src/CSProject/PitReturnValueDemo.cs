namespace samples.CSProject.src
{
    public sealed class PitReturnValueDemo
    {
        public bool KilledReturnsTrue()
        {
            return true;
        }

        public bool KilledReturnsFalse()
        {
            return false;
        }

        public int KilledReturnsZero()
        {
            return 0;
        }

        public int KilledReturnsNonZero()
        {
            return 42;
        }

        public string KilledReturnsString()
        {
            return "ready";
        }

        public object KilledReturnsObject()
        {
            return this;
        }

        public bool SurvivedReturnsTrue()
        {
            return true;
        }

        public bool SurvivedReturnsFalse()
        {
            return false;
        }

        public int SurvivedReturnsZero()
        {
            return 0;
        }

        public int SurvivedReturnsNonZero()
        {
            return 42;
        }

        public string SurvivedReturnsString()
        {
            return "ready";
        }

        public object SurvivedReturnsObject()
        {
            return this;
        }
    }
}

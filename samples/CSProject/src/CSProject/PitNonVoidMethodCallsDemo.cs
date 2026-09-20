namespace samples.CSProject.src
{
    public sealed class PitNonVoidMethodCallsDemo
    {
        public int ReadKilledValue()
        {
            return ComputeValue();
        }

        public int ReadSurvivedValue()
        {
            return ComputeValue();
        }

        public int AssignKilledValue()
        {
            int value = ComputeValue();
            return value;
        }

        public int AssignSurvivedValue()
        {
            int value = ComputeValue();
            return value;
        }

        private int ComputeValue()
        {
            return 9;
        }
    }
}

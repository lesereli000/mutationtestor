namespace samples.CSProject.src
{
    public sealed class PitVoidMethodCallDemo
    {
        public int Count { get; private set; }

        public void Record()
        {
            Count++;
        }

        public int KilledBySideEffectAssertion()
        {
            Record();
            return Count;
        }

        public int SurvivesBecauseSideEffectIsNotAsserted()
        {
            Record();
            return 0;
        }
    }
}

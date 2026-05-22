namespace samples.CSProject.src
{
    public static class PitInvertNegativeDemo
    {
        public static int KilledNegativeConstant()
        {
            return -5;
        }

        public static int KilledNegativeVariable(int value)
        {
            return -value;
        }

        public static int SurvivedNegativeInsideAbsoluteValue()
        {
            int value = -5;
            return Math.Abs(value);
        }

        public static int SurvivedNegativeVariableInsideAbsoluteValue(int value)
        {
            return Math.Abs(-value);
        }
    }
}

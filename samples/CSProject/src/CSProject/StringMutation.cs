namespace CSProject
{
    public static class StringMutation
    {
        public static int Killed(string? value)
        {
            return string.IsNullOrEmpty(value) ? 1 : 0;
        }

        public static int Survived(string? value)
        {
            return string.IsNullOrEmpty(value) ? 1 : 1;
        }
    }
}

namespace samples.CSProject.src
{
    public static class TypeMutationDemo
    {
        public static int UseDouble()
        {
            double a = 5.0 / 2;
            return a == 2.5 ? 1 : 0;
        }

        public static int UseInt()
        {
            int a = 5 / 2;
            return a == 2.5 ? 1 : 0;
        }

        public static int UseFloat()
        {
            float a = 5.0f / 2;
            return a == 2.5f ? 1 : 0;
        }

        public static int UseDouble2()
            {
                double a = 5.0 / 2;
                return a == 2.5 ? 1 : 0;
            }

            public static int UseInt2()
            {
                int a = 5 / 2;
                return a == 2.5 ? 1 : 0;
            }

            public static int UseFloat2()
            {
                float a = 5.0f / 2;
                return a == 2.5f ? 1 : 0;
            }
    }
}





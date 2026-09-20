namespace samples.CSProject.src
{
    public sealed class PitConstructorCallsDemo
    {
        public Widget CreateKilledWidget()
        {
            return new Widget("killed");
        }

        public Widget CreateSurvivedWidget()
        {
            return new Widget("survived");
        }
    }

    public sealed class Widget
    {
        public Widget(string name)
        {
            Name = name;
        }

        public string Name { get; }
    }
}

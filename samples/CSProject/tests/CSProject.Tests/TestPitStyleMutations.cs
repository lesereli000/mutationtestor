using samples.CSProject.src;

namespace samples.CSProject.tests;

public class TestPitStyleMutations
{
    [Fact]
    public void VoidMethodCallKilledGroupAssertsSideEffect()
    {
        var demo = new PitVoidMethodCallDemo();

        Assert.Equal(1, demo.KilledBySideEffectAssertion());
    }

    [Fact]
    public void VoidMethodCallSurvivedGroupIgnoresSideEffect()
    {
        var demo = new PitVoidMethodCallDemo();

        Assert.Equal(0, demo.SurvivesBecauseSideEffectIsNotAsserted());
    }

    [Fact]
    public void IncrementDemoUsesIncrementAndDecrement()
    {
        Assert.Equal(11, PitIncrementDemo.Increment());
        Assert.Equal(9, PitIncrementDemo.Decrement());
    }

    [Fact]
    public void InvertNegativeKilledGroupAssertsNegativeValues()
    {
        Assert.Equal(-5, PitInvertNegativeDemo.KilledNegativeConstant());
        Assert.Equal(-7, PitInvertNegativeDemo.KilledNegativeVariable(7));
    }

    [Fact]
    public void InvertNegativeSurvivedGroupOnlyAssertsMagnitude()
    {
        Assert.Equal(5, PitInvertNegativeDemo.SurvivedNegativeInsideAbsoluteValue());
        Assert.Equal(7, PitInvertNegativeDemo.SurvivedNegativeVariableInsideAbsoluteValue(7));
    }

    [Fact]
    public void ReturnValueKilledGroupAssertsExactValues()
    {
        var demo = new PitReturnValueDemo();

        Assert.True(demo.KilledReturnsTrue());
        Assert.False(demo.KilledReturnsFalse());
        Assert.Equal(0, demo.KilledReturnsZero());
        Assert.Equal(42, demo.KilledReturnsNonZero());
        Assert.Equal("ready", demo.KilledReturnsString());
        Assert.Same(demo, demo.KilledReturnsObject());
    }

    [Fact]
    public void ReturnValueSurvivedGroupUsesWeakAssertions()
    {
        var demo = new PitReturnValueDemo();

        Assert.IsType<bool>(demo.SurvivedReturnsTrue());
        Assert.IsType<bool>(demo.SurvivedReturnsFalse());
        Assert.True(demo.SurvivedReturnsZero() >= 0);
        Assert.True(demo.SurvivedReturnsNonZero() >= 0);
        Assert.NotNull(demo.SurvivedReturnsString());
        Assert.True(demo.SurvivedReturnsObject() == null || demo.SurvivedReturnsObject() != null);
    }
}

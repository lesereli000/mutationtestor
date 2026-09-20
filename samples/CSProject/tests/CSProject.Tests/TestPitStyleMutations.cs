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

    [Fact]
    public void EmptyReturnsKilledGroupAssertsExactValues()
    {
        var demo = new PitEmptyReturnsDemo();

        Assert.Equal("items", demo.KilledReturnsString());
        Assert.Equal(7, demo.KilledReturnsInt());
        Assert.Equal(new[] { 1, 2, 3 }, demo.KilledReturnsList());
        Assert.Equal(new[] { "alpha", "beta" }, demo.KilledReturnsEnumerable());
    }

    [Fact]
    public void EmptyReturnsSurvivedGroupUsesWeakAssertions()
    {
        var demo = new PitEmptyReturnsDemo();

        Assert.NotNull(demo.SurvivedReturnsString());
        Assert.NotNull(demo.SurvivedReturnsList());
    }

    [Fact]
    public void ConstructorCallsKilledGroupAssertsValues()
    {
        var demo = new PitConstructorCallsDemo();

        Assert.Equal("killed", demo.CreateKilledWidget().Name);
    }

    [Fact]
    public void ConstructorCallsSurvivedGroupUsesWeakAssertions()
    {
        var demo = new PitConstructorCallsDemo();

        Assert.True(demo.CreateSurvivedWidget() == null || demo.CreateSurvivedWidget() != null);
    }

    [Fact]
    public void NonVoidMethodCallsKilledGroupAssertsValues()
    {
        var demo = new PitNonVoidMethodCallsDemo();

        Assert.Equal(9, demo.ReadKilledValue());
        Assert.Equal(9, demo.AssignKilledValue());
    }

    [Fact]
    public void NonVoidMethodCallsSurvivedGroupUsesWeakAssertions()
    {
        var demo = new PitNonVoidMethodCallsDemo();

        Assert.True(demo.ReadSurvivedValue() >= 0);
        Assert.True(demo.AssignSurvivedValue() >= 0);
    }
}

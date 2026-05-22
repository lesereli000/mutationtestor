using Xunit;
using samples.CSProject.src;

namespace samples.CSProject.tests;

public class TestWhileLoopContextDemo
{
    [Fact]
    public void NormalizeNegativeToZeroReturnsZeroForNegativeAndZeroInputs()
    {
        Assert.Equal(0, WhileLoopContextDemo.NormalizeNegativeToZero(-5));
        Assert.Equal(0, WhileLoopContextDemo.NormalizeNegativeToZero(0));
    }

    [Fact]
    public void NormalizePositiveToZeroReturnsZeroForPositiveAndZeroInputs()
    {
        Assert.Equal(0, WhileLoopContextDemo.NormalizePositiveToZero(5));
        Assert.Equal(0, WhileLoopContextDemo.NormalizePositiveToZero(0));
    }

    [Fact]
    public void IsWithinLimitIncludesTheBoundary()
    {
        Assert.True(WhileLoopContextDemo.IsWithinLimit(10, 10));
        Assert.False(WhileLoopContextDemo.IsWithinLimit(11, 10));
    }
}

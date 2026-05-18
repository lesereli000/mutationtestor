using Xunit;
using samples.CSProject.src;

namespace samples.CSProject.tests;

public class TestBoundaryCheck
{
    [Fact]
    public void ClampToZeroReturnsZeroForNonPositiveNumbers()
    {
        Assert.Equal(0, BoundaryCheck.ClampToZero(-5));
        Assert.Equal(0, BoundaryCheck.ClampToZero(-1));
        Assert.Equal(0, BoundaryCheck.ClampToZero(0));
    }

    [Fact]
    public void ClampToZeroReturnsPositiveNumbersUnchanged()
    {
        Assert.Equal(5, BoundaryCheck.ClampToZero(5));
        Assert.Equal(100, BoundaryCheck.ClampToZero(100));
    }

    [Fact]
    public void IsUnderLimitReturnsTrueWhenAtOrUnderLimit()
    {
        Assert.True(BoundaryCheck.IsUnderLimit(3, 10));
        Assert.True(BoundaryCheck.IsUnderLimit(0, 5));
        Assert.True(BoundaryCheck.IsUnderLimit(10, 10));
    }

    [Fact]
    public void IsUnderLimitReturnsFalseWhenOverLimit()
    {
        Assert.False(BoundaryCheck.IsUnderLimit(11, 10));
        Assert.False(BoundaryCheck.IsUnderLimit(100, 50));
    }
}

using Xunit;
using samples.CSProject.src;

namespace samples.CSProject.tests;

public class TestRelational
{
    [Fact]
    public void testLessThanKilledDemo()
    {
        Assert.Equal(1, LessThanKilledDemo.IsLessThanFive(4));
        Assert.Equal(0, LessThanKilledDemo.IsLessThanFive(5));
        Assert.Equal(0, LessThanKilledDemo.IsLessThanFive(6));
    }

    [Fact]
    public void testLessEqualKilledDemo()
    {
        Assert.Equal(1, LessEqualKilledDemo.IsLessEqualFive(4));
        Assert.Equal(1, LessEqualKilledDemo.IsLessEqualFive(5));
        Assert.Equal(0, LessEqualKilledDemo.IsLessEqualFive(6));
    }
}
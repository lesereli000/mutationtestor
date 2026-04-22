using Xunit;
using samples.CSProject.src;

namespace samples.CSProject.tests;

public class TestMultiplyDivide
{
    [Fact]
    public void testMultiplyKilled()
    {
        Assert.Equal(12, MultiplyKilledDemo.test());
    }

    [Fact]
    public void testDivideKilled()
    {
        Assert.Equal(4, DivideKilledDemo.test());
    }
}
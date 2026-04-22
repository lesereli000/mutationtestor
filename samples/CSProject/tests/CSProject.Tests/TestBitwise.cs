using Xunit;
using samples.CSProject.src;

namespace samples.CSProject.tests;

public class TestBitwise
{
    [Fact]
    public void testBitwiseAndKilled()
    {
        Assert.Equal(2, BitwiseAndKilledDemo.test());
    }

    [Fact]
    public void testBitwiseOrKilled()
    {
        Assert.Equal(7, BitwiseOrKilledDemo.test());
    }
}
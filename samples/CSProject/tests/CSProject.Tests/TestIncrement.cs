using Xunit;
using samples.CSProject.src;

namespace samples.CSProject.tests;

public class TestIncrement
{
    [Fact]
    public void testKilled()
    {
        Assert.Equal(2, IncrementKilledDemo.test());
    }
}
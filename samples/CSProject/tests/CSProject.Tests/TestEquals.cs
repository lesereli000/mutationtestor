using Xunit;
using samples.CSProject.src;

namespace samples.CSProject.tests;

public class TestEquals
{
    [Fact]
    public void testEqual()
    {
        Assert.Equal(1, EqualsDemo.isEqual(5));
        Assert.Equal(0, EqualsDemo.isEqual(3));
    }

    [Fact]
    public void testNotEqual()
    {
        Assert.Equal(0, NotEqualsDemo.isNotEqual(5));
        Assert.Equal(1, NotEqualsDemo.isNotEqual(3));
    }
}
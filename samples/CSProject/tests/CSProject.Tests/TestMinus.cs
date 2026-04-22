using Xunit;
using samples.CSProject.src;

namespace samples.CSProject.tests;

public class TestMinus
{
    [Fact]
    public void testMinus1()
    {
        Assert.Equal(4, Minus1.minus1(5));
    }

    [Fact]
    public void testMinus2()
    {
        Assert.Equal(3, Minus2.minus2(5));
    }
}
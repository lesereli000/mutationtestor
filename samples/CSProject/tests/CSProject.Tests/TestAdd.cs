using Xunit;
using samples.CSProject.src;

namespace samples.CSProject.tests;

public class TestAdd
{
    [Fact]
    public void testAdd1()
    {
        Assert.Equal(6, Add1.add1(5));
        Assert.Equal(0, Add1.add1(-1));
        Assert.Equal(789, Add1.add1(788));
        Assert.Equal(12, Add1.add1(11));
        Assert.Equal(int.MaxValue, Add1.add1(int.MaxValue - 1));
    }

    [Fact]
    public void testAdd2()
    {
        Assert.Equal(7.0, Add2.add2(5));
        Assert.Equal(1.0, Add2.add2(-1));
        Assert.Equal(790.0, Add2.add2(788));
        Assert.Equal(13.0, Add2.add2(11));
        Assert.Equal((double)int.MaxValue, Add2.add2(int.MaxValue - 2));
    }

    [Fact]
    public void testProgram()
    {
        Assert.Equal(20.75f, Program.RunPipeline(5));
    }
}
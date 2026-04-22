using Xunit;
using samples.CSProject.src;

namespace samples.CSProject.tests;

public class TestModulo
{
    [Fact]
    public void testModuloKilled()
    {
        Assert.Equal(1, ModuloKilledDemo.test());
    }
}
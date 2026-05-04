using Xunit;
using samples.CSProject.src;

namespace samples.CSProject.tests;

public class TestNoCoverage
{
    [Fact]
    public void covered_method_is_executed_by_tests()
    {
        Assert.Equal(6, NoCoverageDemo.CoveredByTests(5));
    }
}

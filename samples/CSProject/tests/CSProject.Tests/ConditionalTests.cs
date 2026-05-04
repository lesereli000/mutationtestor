using Xunit;
using CSProject;

namespace CSProject.Tests
{
    public class ConditionalTests
    {
        [Fact]
        public void ConditionalTrue_Killed()
        {
            Assert.Equal(0, ConditionalDemo.Killed(50));
        }

        [Fact]
        public void ConditionalFalse_Killed()
        {
            Assert.Equal(1, ConditionalDemo.Killed(150));
        }
    }
}
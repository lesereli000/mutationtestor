using Xunit;
using CSProject;

namespace CSProject.Tests
{
    public class TestStringMutation
    {
        [Fact]
        public void StringMutation_Killed()
        {
            Assert.Equal(1, StringMutation.Killed(null));
            Assert.Equal(1, StringMutation.Killed(string.Empty));
        }

        [Fact]
        public void StringMutation_Survived()
        {
            Assert.Equal(1, StringMutation.Survived("text"));
        }
    }
}

using Xunit;
using CSProject;

namespace CSProject.Tests
{
    public class TestStringMutationToNotEmpty
    {
        [Fact]
        public void StringMutationToNotEmpty_Killed()
        {
            Assert.Equal(1, StringMutationToNotEmptyDemo.Killed(string.Empty));
        }

        [Fact]
        public void StringMutationToNotEmpty_Survived()
        {
            Assert.Equal(1, StringMutationToNotEmptyDemo.Survived(null));
        }
    }
}

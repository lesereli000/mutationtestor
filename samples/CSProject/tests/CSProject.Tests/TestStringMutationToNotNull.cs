using Xunit;
using CSProject;

namespace CSProject.Tests
{
    public class TestStringMutationToNotNull
    {
        [Fact]
        public void StringMutationToNotNull_Killed()
        {
            Assert.Equal(1, StringMutationToNotNullDemo.Killed(null));
        }

        [Fact]
        public void StringMutationToNotNull_Survived()
        {
            Assert.Equal(1, StringMutationToNotNullDemo.Survived(string.Empty));
        }
    }
}

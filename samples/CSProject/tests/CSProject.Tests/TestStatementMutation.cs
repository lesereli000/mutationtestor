using Xunit;
using CSProject;

namespace CSProject.Tests
{
    public class TestStatementMutation
    {
        [Fact]
        public void StatementMutation_Killed()
        {
            Assert.Equal(1, StatementMutationDemo.Killed());
        }

        [Fact]
        public void StatementMutation_Survived()
        {
            Assert.Equal(1, StatementMutationDemo.Survived());
        }
    }
}

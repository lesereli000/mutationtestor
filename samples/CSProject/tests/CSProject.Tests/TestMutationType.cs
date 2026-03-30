using Xunit;
using samples.CSProject.src;

namespace CSProject.Tests
{
    public class TestTypeMutation
    {
        [Fact]
        public void Test_UseDouble_ShouldDetectDoubleBehavior()
        {
            Assert.Equal(1, TypeMutationDemo.UseDouble());
        }

        [Fact]
        public void Test_UseInt_ShouldDetectIntBehavior()
        {
            Assert.Equal(0, TypeMutationDemo.UseInt());
        }

        [Fact]
        public void Test_UseFloat_ShouldDetectFloatBehavior()
        {
            Assert.Equal(1, TypeMutationDemo.UseFloat());
        }
    }
}
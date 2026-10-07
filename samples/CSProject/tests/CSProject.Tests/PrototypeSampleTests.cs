using System;
using Xunit;

namespace CSProject.Tests
{
    public class PrototypeSampleTests
    {
        [Fact]
        public void TestMutationDemo()
        {
            // Call RunDemo
            AllSyntaxDemo.RunDemo();

            // Cover other methods
            AllSyntaxDemo.StaticMethod();
            
            var demo = new AllSyntaxDemo();
            Assert.Equal(6, demo.Sum(1, 2, 3));

            var dog = new Dog("Beagle");
            dog.MakeSound();
            Assert.Equal("Beagle", dog.Breed);

            var box = new Box<int>(1);
            Assert.Equal(1, box.Value);

            var greeter = new ConsoleGreeter();
            greeter.Greet("Test");
        }
    }
}

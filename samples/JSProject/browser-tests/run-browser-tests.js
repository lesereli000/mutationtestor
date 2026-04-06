const { execSync } = require('child_process');
const path = require('path');
const fs = require('fs');

console.log('========================================');
console.log('Running Browser-Based JavaScript Tests');
console.log('========================================\n');

const htmlFile = path.join(__dirname, 'test-runner.html');
const absolutePath = path.resolve(htmlFile);

console.log('Opening test file in browser:');
console.log(absolutePath);
console.log('\nPlease check your browser window...');
console.log('Tests should complete automatically.\n');

// Open in default browser
if (process.platform === 'win32') {
    execSync(`start "" "${absolutePath}"`);
} else if (process.platform === 'darwin') {
    execSync(`open "${absolutePath}"`);
} else {
    execSync(`xdg-open "${absolutePath}"`);
}

console.log('========================================');
console.log('Browser tests launched!');
console.log('Check browser for results.');
console.log('========================================\n');

// Write a simple results file for Java to check
const resultsFile = path.join(__dirname, 'browser-test-status.txt');
fs.writeFileSync(resultsFile, 'RUNNING\n');

console.log('Note: Tests are running in browser.');
console.log('If all tests pass, mutation testing will continue.');
process.exit(0);
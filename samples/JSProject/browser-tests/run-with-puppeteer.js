const puppeteer = require('puppeteer');
const path = require('path');
const fs = require('fs');

(async () => {
    console.log('========================================');
    console.log('JavaScript Mutation Testing');
    console.log('Puppeteer (Chrome Headless) Browser Test Runner');
    console.log('========================================\n');

    let browser;
    let exitCode = 0;

    try {
        browser = await puppeteer.launch({
            headless: true,
            args: ['--no-sandbox', '--disable-setuid-sandbox']
        });

        const page = await browser.newPage();

        page.on('console', msg => {
            const text = msg.text();
            if (text.includes('===') || text.includes('Tests complete')) {
                console.log('[BROWSER]', text);
            }
        });

        page.on('pageerror', error => {
            console.log('[JS ERROR]', error.message);
        });

        // Try to find test-runner.html in multiple locations
        let testFile = null;
        const possiblePaths = [
            path.resolve(__dirname, 'test-runner.html'),
            path.resolve(__dirname, '..', 'samples', 'JSProject', 'browser-tests', 'test-runner.html'),
            path.resolve(__dirname, '..', '..', 'samples', 'JSProject', 'browser-tests', 'test-runner.html'),
            path.resolve(__dirname, '..', '..', '..', 'samples', 'JSProject', 'browser-tests', 'test-runner.html')
        ];

        for (const testPath of possiblePaths) {
            if (fs.existsSync(testPath)) {
                testFile = 'file://' + testPath;
                break;
            }
        }

        if (!testFile) {
            console.error('ERROR: Could not find test-runner.html');
            console.error('Searched in:');
            possiblePaths.forEach(p => console.error('  -', p));
            process.exit(1);
        }

        console.log('Loading:', testFile);
        console.log('');

        await page.goto(testFile, { 
            waitUntil: 'networkidle0',
            timeout: 30000
        });

        await page.waitForFunction(
            () => {
                return window.testsComplete === true ||
                       document.getElementById('phantomjs-results') !== null ||
                       (window.testResults && window.testResults.total > 0);
            },
            { timeout: 15000 }
        );

        console.log('Tests completed!\n');

        const results = await page.evaluate(() => {
            const resultsDiv = document.getElementById('phantomjs-results');
            const output = document.getElementById('test-output');
            const summary = document.getElementById('summary');

            if (resultsDiv) {
                try {
                    return {
                        summary: JSON.parse(resultsDiv.textContent),
                        output: output ? output.innerText : 'No output',
                        summaryText: summary ? summary.innerText : 'No summary',
                        success: true
                    };
                } catch (e) {
                    return {
                        success: false,
                        error: 'Failed to parse results: ' + e.toString()
                    };
                }
            }

            if (window.testResults) {
                return {
                    summary: window.testResults,
                    output: output ? output.innerText : 'No output',
                    summaryText: summary ? summary.innerText : 'No summary',
                    success: true
                };
            }

            return {
                success: false,
                error: 'No test results found'
            };
        });

        if (!results.success) {
            console.log('ERROR:', results.error);
            exitCode = 1;
        } else {
            // Print results WITHOUT emojis
            console.log(results.summaryText);

            if (results.summary && results.summary.allPassed) {
                console.log('\nALL TESTS PASSED\n');  // NO EMOJI
                exitCode = 0;
            } else {
                console.log('\nSOME TESTS FAILED\n');  // NO EMOJI
                exitCode = 1;
            }
        }

    } catch (error) {
        console.error('ERROR:', error.message);
        exitCode = 1;
    } finally {
        if (browser) {
            await browser.close();
        }
        process.exit(exitCode);
    }
})();
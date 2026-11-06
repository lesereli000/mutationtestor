@echo off
cd /d "%~dp0"
echo ========================================
echo JavaScript Mutation Testing
echo Browser-Based Manual Testing
echo ========================================
echo.
echo STEP 1: Opening test file in browser...
echo.

REM Open test file in default browser
start browser-tests\test-runner.html

echo Browser should open showing test results.
echo.
echo ========================================
echo VERIFY TESTS
echo ========================================
echo.
echo Check the browser window:
echo - Look for "TEST SUMMARY" at the bottom
echo - Verify "Success Rate: 100.0%%"
echo.
echo ========================================
echo.

choice /C YN /M "Did all tests PASS (Success Rate 100%%)?"

if errorlevel 2 (
    echo.
    echo Tests FAILED - Fix tests before running mutations!
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo Tests PASSED - Starting Mutation Testing
echo ========================================
echo.

REM Run the operator spec from the repository root
pushd ..\..
java -jar target\MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar samples/JSProject configs/samples/javascript-manual.config
popd
echo.
echo ========================================
echo Mutation testing complete!
echo Check output above for results.
echo ========================================
pause
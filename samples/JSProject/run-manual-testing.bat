@echo off
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

REM Update config and run first mutation type
echo source_code_directory=src > config.txt
echo mutator_type=NLT_INTEGER_TO_FLOAT >> config.txt
echo compile_command=echo "No compilation" >> config.txt
echo test_command=start browser-tests\test-runner.html ^&^& timeout /t 3 ^&^& exit 0 >> config.txt

cd ..\..
java -jar target\MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar samples/JSProject samples/JSProject/config.txt

cd samples\JSProject
echo.
echo ========================================
echo Mutation testing complete!
echo Check output above for results.
echo ========================================
pause
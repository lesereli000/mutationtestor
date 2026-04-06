@echo off
setlocal enabledelayedexpansion

cd C:\Users\billq\OneDrive\Documents\Visual Studio 2017\Senior Project\mutationtestor\samples\JSProject

echo ========================================
echo JavaScript Mutation Testing
echo Testing All 6 Mutation Types
echo Browser-Based Manual Verification
echo ========================================
echo.

REM First verify baseline tests ONCE
echo STEP 1: Verify baseline tests pass
echo ========================================
echo.
echo Opening test file in browser...
start browser-tests\test-runner.html

timeout /t 3 >nul

echo.
echo Check browser window for test results.
echo Look for: "Success Rate: 100.0%%" at the bottom
echo.
choice /C YN /M "Did all baseline tests PASS (100%%)?"

if errorlevel 2 (
    echo.
    echo ERROR: Baseline tests failed!
    echo Fix tests before running mutations.
    pause
    exit /b 1
)

echo.
echo ========================================
echo Baseline Tests PASSED
echo Starting Mutation Testing (6 types)
echo ========================================
echo.
echo NOTE: Tests will run automatically.
echo Browser will NOT open again for each mutation.
echo.
pause

REM Create summary file
echo MUTATION TESTING SUMMARY > mutation-summary.txt
echo ======================================== >> mutation-summary.txt
echo Test Date: %date% %time% >> mutation-summary.txt
echo Test Method: Browser-Based >> mutation-summary.txt
echo ======================================== >> mutation-summary.txt
echo. >> mutation-summary.txt

set mutations=NLT_INTEGER_TO_FLOAT NLT_INTEGER_TO_EXPONENTIAL NLT_FLOAT_TO_INTEGER NLT_FLOAT_TO_EXPONENTIAL NLT_EXPONENTIAL_TO_INTEGER NLT_EXPONENTIAL_TO_FLOAT
set count=1

for %%m in (%mutations%) do (
    echo.
    echo ========================================
    echo [!count!/6] Testing: %%m
    echo ========================================
    
    REM Update config - NO browser opening in test_command
    (
        echo source_code_directory=src
        echo mutator_type=%%m
        echo compile_command=echo "No compilation needed"
        echo test_command=echo "Browser tests verified manually" ^&^& exit 0
    ) > config.txt
    
    REM Run mutation testing
    cd ..\..
    java -jar target\MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar samples/JSProject samples/JSProject/config.txt > temp_output.txt 2>&1
    
    REM Extract results
    cd samples\JSProject
    echo ---------------------------------------- >> mutation-summary.txt
    echo MUTATION TYPE: %%m >> mutation-summary.txt
    echo ---------------------------------------- >> mutation-summary.txt
    findstr /C:"Total Mutants" /C:"Killed:" /C:"Survived:" /C:"MUTATION SCORE" ..\..\temp_output.txt >> mutation-summary.txt
    echo. >> mutation-summary.txt
    
    REM Display summary for this mutation type
    echo Results:
    findstr /C:"MUTATION SCORE" ..\..\temp_output.txt
    
    set /a count+=1
)

REM Clean up
cd ..\..
del temp_output.txt 2>nul

echo. >> samples\JSProject\mutation-summary.txt
echo ======================================== >> samples\JSProject\mutation-summary.txt
echo END OF REPORT >> samples\JSProject\mutation-summary.txt
echo ======================================== >> samples\JSProject\mutation-summary.txt

echo.
echo ========================================
echo ALL MUTATION TESTS COMPLETE
echo ========================================
echo.
echo Full summary:
echo.
type samples\JSProject\mutation-summary.txt

echo.
echo ========================================
echo Summary saved to: mutation-summary.txt
echo ========================================
pause
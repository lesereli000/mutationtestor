@echo off
setlocal enabledelayedexpansion

cd C:\Users\billq\OneDrive\Documents\Visual Studio 2017\Senior Project\mutationtestor\samples\JSProject

echo ========================================
echo JavaScript Mutation Testing
echo Puppeteer (Chrome Headless) Automated Testing
echo ========================================
echo.

REM First verify baseline tests
echo Running baseline tests in browser...
cd browser-tests
call node run-with-puppeteer.js

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Baseline tests failed!
    echo Fix tests before running mutations.
    cd ..
    pause
    exit /b 1
)

cd ..

echo.
echo ========================================
echo Baseline Tests PASSED
echo Starting Mutation Testing (6 types)
echo ========================================
echo.

REM Create summary file
echo MUTATION TESTING SUMMARY > mutation-summary.txt
echo ======================================== >> mutation-summary.txt
echo Test Date: %date% %time% >> mutation-summary.txt
echo Test Method: Puppeteer (Chrome Headless Browser) >> mutation-summary.txt
echo ======================================== >> mutation-summary.txt
echo. >> mutation-summary.txt

set mutations=NLT_INTEGER_TO_FLOAT NLT_INTEGER_TO_EXPONENTIAL NLT_FLOAT_TO_INTEGER NLT_FLOAT_TO_EXPONENTIAL NLT_EXPONENTIAL_TO_INTEGER NLT_EXPONENTIAL_TO_FLOAT
set count=1

for %%m in (%mutations%) do (
    echo.
    echo ========================================
    echo [!count!/6] Testing: %%m
    echo ========================================
    
    REM Update config
    (
        echo source_code_directory=src
        echo mutator_type=%%m
        echo compile_command=echo "No compilation needed"
        echo test_command=cd browser-tests ^&^& node run-with-puppeteer.js
    ) > config.txt
    
    REM Run mutation testing - capture ALL output
    cd ..\..
    java -jar target\MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar samples/JSProject samples/JSProject/config.txt > samples\JSProject\temp_%%m.txt 2>&1
    
    REM Go back to JSProject
    cd samples\JSProject
    
    REM Extract and display results
    echo ---------------------------------------- >> mutation-summary.txt
    echo MUTATION TYPE: %%m >> mutation-summary.txt
    echo ---------------------------------------- >> mutation-summary.txt
    
    REM Use type to display entire temp file, then filter
    type temp_%%m.txt | findstr /C:"Total Mutants" /C:"Killed:" /C:"Survived:" /C:"MUTATION SCORE" >> mutation-summary.txt
    
    echo. >> mutation-summary.txt
    
    REM Display to console
    echo Results:
    type temp_%%m.txt | findstr /C:"MUTATION SCORE"
    
    set /a count+=1
)

REM Clean up temp files
del temp_*.txt 2>nul

echo. >> mutation-summary.txt
echo ======================================== >> mutation-summary.txt
echo SUMMARY >> mutation-summary.txt
echo ======================================== >> mutation-summary.txt
echo All 6 numeric literal type mutations tested >> mutation-summary.txt
echo See detailed results above >> mutation-summary.txt
echo ======================================== >> mutation-summary.txt

echo.
echo ========================================
echo ALL MUTATION TESTS COMPLETE
echo ========================================
echo.
type mutation-summary.txt

echo.
echo ========================================
echo Summary saved to: mutation-summary.txt
echo ========================================
pause
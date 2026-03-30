@echo off
setlocal enabledelayedexpansion

cd C:\Users\billq\OneDrive\Documents\Visual Studio 2017\Senior Project\mutationtestor

echo ========================================
echo Running All 6 Mutation Types
echo ========================================
echo.

REM Create summary file
echo COMPREHENSIVE MUTATION TESTING SUMMARY > mutation-summary.txt
echo ======================================== >> mutation-summary.txt
echo Test Date: %date% %time% >> mutation-summary.txt
echo ======================================== >> mutation-summary.txt
echo. >> mutation-summary.txt

set mutations=NLT_INTEGER_TO_FLOAT NLT_INTEGER_TO_EXPONENTIAL NLT_FLOAT_TO_INTEGER NLT_FLOAT_TO_EXPONENTIAL NLT_EXPONENTIAL_TO_INTEGER NLT_EXPONENTIAL_TO_FLOAT
set count=1

for %%m in (%mutations%) do (
    echo [!count!/6] Testing %%m...
    
    REM Update config
    (
        echo source_code_directory=src
        echo mutator_type=%%m
        echo compile_command=npm install
        echo test_command=npm test
    ) > samples\JSProject\config.txt
    
    REM Run mutation testing and capture output
    java -jar target\MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar samples/JSProject samples/JSProject/config.txt > temp_output.txt 2>&1
    
    REM Extract mutation score and append to summary
    echo ---------------------------------------- >> mutation-summary.txt
    echo MUTATION TYPE: %%m >> mutation-summary.txt
    echo ---------------------------------------- >> mutation-summary.txt
    findstr /C:"Total Mutants" /C:"Killed:" /C:"Survived:" /C:"MUTATION SCORE" temp_output.txt >> mutation-summary.txt
    echo. >> mutation-summary.txt
    
    set /a count+=1
)

REM Add final summary
echo ======================================== >> mutation-summary.txt
echo END OF REPORT >> mutation-summary.txt
echo ======================================== >> mutation-summary.txt

REM Clean up
del temp_output.txt 2>nul

echo.
echo ========================================
echo ALL TESTS COMPLETE!
echo ========================================
echo.
echo Summary saved to: mutation-summary.txt
echo Opening summary...
echo.

type mutation-summary.txt
echo.
pause
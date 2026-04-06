@echo off
setlocal enabledelayedexpansion

cd C:\Users\billq\OneDrive\Documents\Visual Studio 2017\Senior Project\mutationtestor

echo ========================================
echo Running All 6 Mutation Types
echo ========================================
echo.

set "mutations=NLT_INTEGER_TO_FLOAT NLT_INTEGER_TO_EXPONENTIAL NLT_FLOAT_TO_INTEGER NLT_FLOAT_TO_EXPONENTIAL NLT_EXPONENTIAL_TO_INTEGER NLT_EXPONENTIAL_TO_FLOAT"

for %%m in (%mutations%) do (
    echo.
    echo ========================================
    echo Testing: %%m
    echo ========================================
    
    REM Update config.txt
    (
        echo source_code_directory=src
        echo mutator_type=%%m
        echo compile_command=npm install
        echo test_command=npm test
    ) > samples\JSProject\config.txt
    
    REM Run mutation testing
    java -jar target\MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar samples/JSProject samples/JSProject/config.txt
    
    echo.
    echo Completed: %%m
    echo.
    pause
)

echo.
echo ========================================
echo ALL MUTATION TYPES TESTED
echo ========================================
pause
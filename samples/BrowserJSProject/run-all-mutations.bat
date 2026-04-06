@echo off
setlocal enabledelayedexpansion

echo ========================================
echo Browser JS - Run All Mutation Types
echo ========================================
echo.

set "mutations=AOR_PLUS_TO_MINUS AOR_MINUS_TO_PLUS ROR_GT_TO_GE ROR_GE_TO_GT LOGICAL_AND_TO_OR LOGICAL_OR_TO_AND REMOVE_INCREMENT NLT_INTEGER_TO_FLOAT NLT_INTEGER_TO_EXPONENTIAL NLT_FLOAT_TO_INTEGER NLT_FLOAT_TO_EXPONENTIAL NLT_EXPONENTIAL_TO_INTEGER NLT_EXPONENTIAL_TO_FLOAT"

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
    ) > config.txt

    REM Run mutation testing from project root
    cd ..\..
    java -jar target\MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar samples/BrowserJSProject samples/BrowserJSProject/config.txt
    cd samples\BrowserJSProject

    echo.
    echo Completed: %%m
    echo.
)

echo.
echo ========================================
echo ALL MUTATION TYPES TESTED
echo ========================================
pause

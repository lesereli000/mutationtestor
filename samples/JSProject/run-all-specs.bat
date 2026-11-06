@echo off
setlocal enabledelayedexpansion
REM Runs every spec in specs\ against this project, testing each mutant with
REM Puppeteer (headless Chrome), and writes the scores to mutation-summary.txt.

REM This project's folder, and the repository root two levels up
set "PROJECT=%~dp0"
cd /d "%PROJECT%..\.."

set JAR=target\MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar
if not exist %JAR% (
    echo [INFO] JAR not found. Building...
    call mvn package -q -DskipTests || exit /b 1
)

echo Running baseline tests in browser...
pushd "%PROJECT%browser-tests"
call node run-with-puppeteer.js
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Baseline tests failed! Fix tests before running mutations.
    popd
    exit /b 1
)
popd

set "SUMMARY=%PROJECT%mutation-summary.txt"
echo MUTATION TESTING SUMMARY > "%SUMMARY%"
echo Test Date: %date% %time% >> "%SUMMARY%"
echo Test Method: Puppeteer (Chrome Headless Browser) >> "%SUMMARY%"
echo. >> "%SUMMARY%"

set "CONFIG=%TEMP%\jsproject-spec.config"
for %%s in (specs\*.spec) do (
    echo.
    echo ========================================
    echo Testing: %%~nxs
    echo ========================================
    (
        echo source_code_directory=src
        echo custom_mutation_spec=%%s
        echo compile_command=echo "No compilation needed"
        echo test_command=cd browser-tests ^&^& node run-with-puppeteer.js
    ) > "!CONFIG!"

    java -jar %JAR% samples/JSProject "!CONFIG!" > "%TEMP%\jsproject-%%~ns.txt" 2>&1

    echo ---------------------------------------- >> "%SUMMARY%"
    echo SPEC: %%s >> "%SUMMARY%"
    findstr /C:"Total Mutants" /C:"Killed:" /C:"Survived:" /C:"MUTATION SCORE" "%TEMP%\jsproject-%%~ns.txt" >> "%SUMMARY%"
    echo. >> "%SUMMARY%"
    findstr /C:"MUTATION SCORE" "%TEMP%\jsproject-%%~ns.txt"
    del "%TEMP%\jsproject-%%~ns.txt" 2>nul
)
del "!CONFIG!" 2>nul

echo.
type "%SUMMARY%"
echo Summary saved to: %SUMMARY%

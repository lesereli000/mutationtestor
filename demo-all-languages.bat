@echo off
REM ============================================================================
REM  demo-all-languages.bat
REM  Demonstrates mutation testing across all 5 supported languages
REM  using a single compiled JAR.
REM
REM  Usage: Run from the project root directory
REM         demo-all-languages.bat
REM
REM  Reports are saved to: demo-output\{java,javascript,csharp,python,scheme}\
REM ============================================================================

setlocal enabledelayedexpansion

set JAR=target\MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar
set DEMO_DIR=demo-output
set PASS=0
set FAIL=0

echo.
echo ============================================================================
echo   MUTATION TESTER - Multi-Language Demo
echo   Running all 5 language projects with the same JAR
echo ============================================================================
echo.

REM Check JAR exists
if not exist "%JAR%" (
    echo [INFO] JAR not found. Building...
    call mvn package -q -DskipTests
    if errorlevel 1 (
        echo [ERROR] Build failed. Exiting.
        exit /b 1
    )
)

REM Clean previous demo output
if exist "%DEMO_DIR%" rmdir /s /q "%DEMO_DIR%"
mkdir "%DEMO_DIR%"

REM --------------------------------------------------------------------------
echo.
echo ============================================================================
echo   [1/5] JAVA (Maven Project) - AOR_PLUS_TO_MINUS
echo ============================================================================
echo.
java -jar %JAR% samples/MavenProject maven.config
if errorlevel 1 (set /a FAIL+=1) else (set /a PASS+=1)
if exist output\report xcopy /s /q /y output\report "%DEMO_DIR%\java\" >nul 2>&1

REM --------------------------------------------------------------------------
echo.
echo ============================================================================
echo   [2/5] JAVASCRIPT (Browser-Based) - NLT_EXPONENTIAL_TO_FLOAT
echo ============================================================================
echo.
java -jar %JAR% samples/JSProject samples/JSProject/config.txt
if errorlevel 1 (set /a FAIL+=1) else (set /a PASS+=1)
if exist output\report xcopy /s /q /y output\report "%DEMO_DIR%\javascript\" >nul 2>&1

REM --------------------------------------------------------------------------
echo.
echo ============================================================================
echo   [3/5] C# (.NET Project) - REMOVE_INCREMENT
echo ============================================================================
echo.
java -jar %JAR% samples/CSProject cs.config
if errorlevel 1 (set /a FAIL+=1) else (set /a PASS+=1)
if exist output\report xcopy /s /q /y output\report "%DEMO_DIR%\csharp\" >nul 2>&1

REM --------------------------------------------------------------------------
echo.
echo ============================================================================
echo   [4/5] PYTHON (Pytest Project) - AOR_PLUS_TO_MINUS
echo ============================================================================
echo.
java -jar %JAR% samples/PytestProject python.config
if errorlevel 1 (set /a FAIL+=1) else (set /a PASS+=1)
if exist output\report xcopy /s /q /y output\report "%DEMO_DIR%\python\" >nul 2>&1

REM --------------------------------------------------------------------------
echo.
echo ============================================================================
echo   [5/5] SCHEME/RACKET - TYPE_INT_TO_DOUBLE
echo ============================================================================
echo.
java -jar %JAR% samples/SchemeProject scheme.config
if errorlevel 1 (set /a FAIL+=1) else (set /a PASS+=1)
if exist output\report xcopy /s /q /y output\report "%DEMO_DIR%\scheme\" >nul 2>&1

REM --------------------------------------------------------------------------
echo.
echo ============================================================================
echo   DEMO COMPLETE
echo ============================================================================
echo   Projects passed: %PASS% / 5
if %FAIL% gtr 0 (
    echo   Projects failed: %FAIL% / 5
    echo   NOTE: Some failures may be due to missing runtimes
    echo         (e.g. dotnet, racket, pytest, node/npm)
)
echo.
echo   All reports saved to: %DEMO_DIR%\
echo     demo-output\java\          - mutation-report-demo.html
echo     demo-output\javascript\    - mutation-report-demo.html
echo     demo-output\csharp\        - mutation-report-demo.html
echo     demo-output\python\        - mutation-report-demo.html
echo     demo-output\scheme\        - mutation-report-demo.html
echo ============================================================================
echo.

endlocal

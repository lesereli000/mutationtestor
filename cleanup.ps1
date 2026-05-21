$root = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "[1/6] Removing Maven build target..."
Remove-Item -Recurse -Force "$root\target" -ErrorAction SilentlyContinue

Write-Host "[2/6] Removing Python virtual environment..."
Remove-Item -Recurse -Force "$root\samples\PytestProject\.venv" -ErrorAction SilentlyContinue

Write-Host "[3/6] Removing JavaScript node_modules and lockfile..."
Remove-Item -Recurse -Force "$root\samples\JSProject\node_modules" -ErrorAction SilentlyContinue
Remove-Item -Force "$root\samples\JSProject\package-lock.json" -ErrorAction SilentlyContinue

Write-Host "[4/6] Removing .NET build artifacts..."
Remove-Item -Recurse -Force "$root\samples\CSProject\tests\CSProject.Tests\bin" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$root\samples\CSProject\tests\CSProject.Tests\obj" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$root\samples\CSProject\src\CSProject\bin" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$root\samples\CSProject\src\CSProject\obj" -ErrorAction SilentlyContinue

Write-Host "[5/6] Removing Racket compiled directories..."
Remove-Item -Recurse -Force "$root\samples\RacketProject\project\compiled" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$root\samples\RacketCalculator\calculator\compiled" -ErrorAction SilentlyContinue

Write-Host "[6/6] Removing Maven sample project target..."
Remove-Item -Recurse -Force "$root\samples\MavenProject\target" -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "Cleanup complete!" -ForegroundColor Green

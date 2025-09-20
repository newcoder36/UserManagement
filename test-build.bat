@echo off
REM Test build script to verify Docker build works

title NSE Bot - Test Build

echo.
echo ========================================
echo  NSE Bot - Testing Docker Build
echo ========================================
echo.

echo [INFO] Testing development Docker build...

REM Clean any existing containers first
docker-compose -f docker-compose.dev.yml down 2>nul

REM Test build
docker-compose -f docker-compose.dev.yml build nse-bot

if %errorlevel% equ 0 (
    echo.
    echo [SUCCESS] Docker build completed successfully!
    echo [INFO] You can now run: scripts\dev-setup.bat
) else (
    echo.
    echo [ERROR] Docker build failed!
    echo [INFO] Check the error messages above for details
)

echo.
pause
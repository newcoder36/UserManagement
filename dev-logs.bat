@echo off
REM NSE Stock Analysis Bot - Development Logs Viewer
REM Shows real-time logs from the development environment

title NSE Bot - Development Logs

echo.
echo ========================================
echo  NSE Stock Analysis Bot - Dev Logs
echo ========================================
echo.

REM Check if Docker is running
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker is not running or not installed
    echo Please start Docker Desktop and try again
    pause
    exit /b 1
)

REM Check if development environment is running
docker-compose -f docker-compose.dev.yml ps | findstr "nse-bot-app-dev" >nul
if %errorlevel% neq 0 (
    echo [WARNING] Development environment might not be running
    echo Starting development environment...
    echo.
    call scripts\dev-setup.bat start
    echo.
)

echo [INFO] Showing real-time logs from NSE Bot development environment
echo [INFO] Press Ctrl+C to stop viewing logs
echo.
echo ----------------------------------------
echo  Application Logs (nse-bot service)
echo ----------------------------------------
echo.

REM Follow logs for the main application
docker-compose -f docker-compose.dev.yml logs -f --tail=50 nse-bot

echo.
echo [INFO] Log viewing stopped
pause
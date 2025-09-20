@echo off
REM NSE Stock Analysis Bot - All Services Logs Viewer
REM Shows logs from all development services

title NSE Bot - All Development Logs

echo.
echo ========================================
echo  NSE Stock Analysis Bot - All Logs
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

echo [INFO] Showing logs from all development services
echo [INFO] Press Ctrl+C to stop viewing logs
echo.

REM Menu for log selection
echo Choose log view:
echo [1] All Services (combined)
echo [2] NSE Bot Application only
echo [3] Database (PostgreSQL) only
echo [4] Cache (Redis) only
echo [5] Last 100 lines from all services
echo.
set /p choice="Enter your choice (1-5): "

if "%choice%"=="1" goto :all_logs
if "%choice%"=="2" goto :app_logs
if "%choice%"=="3" goto :db_logs
if "%choice%"=="4" goto :redis_logs
if "%choice%"=="5" goto :recent_logs
goto :all_logs

:all_logs
echo.
echo ----------------------------------------
echo  All Services Logs (Real-time)
echo ----------------------------------------
echo.
docker-compose -f docker-compose.dev.yml logs -f
goto :end

:app_logs
echo.
echo ----------------------------------------
echo  NSE Bot Application Logs (Real-time)
echo ----------------------------------------
echo.
docker-compose -f docker-compose.dev.yml logs -f --tail=50 nse-bot
goto :end

:db_logs
echo.
echo ----------------------------------------
echo  PostgreSQL Database Logs (Real-time)
echo ----------------------------------------
echo.
docker-compose -f docker-compose.dev.yml logs -f --tail=30 postgres
goto :end

:redis_logs
echo.
echo ----------------------------------------
echo  Redis Cache Logs (Real-time)
echo ----------------------------------------
echo.
docker-compose -f docker-compose.dev.yml logs -f --tail=30 redis
goto :end

:recent_logs
echo.
echo ----------------------------------------
echo  Recent Logs (Last 100 lines each)
echo ----------------------------------------
echo.
echo === NSE Bot Application ===
docker-compose -f docker-compose.dev.yml logs --tail=100 nse-bot
echo.
echo === PostgreSQL Database ===
docker-compose -f docker-compose.dev.yml logs --tail=50 postgres
echo.
echo === Redis Cache ===
docker-compose -f docker-compose.dev.yml logs --tail=50 redis
echo.
echo [INFO] Recent logs displayed
pause
goto :end

:end
echo.
echo [INFO] Log viewing stopped
@echo off
REM NSE Stock Analysis Bot - Quick Start for Development
REM One-click setup and launch for development

title NSE Bot - Quick Start

echo.
echo ========================================
echo  NSE Stock Analysis Bot - Quick Start
echo ========================================
echo.

REM Check if .env file exists
if not exist ".env" (
    echo [INFO] Setting up environment file for first time...
    if exist ".env.template" (
        copy ".env.template" ".env" >nul
        echo [WARNING] Please configure your bot token in .env file
        echo.
        echo Required steps:
        echo 1. Open .env file in a text editor
        echo 2. Replace 'your_bot_token_here' with your actual bot token
        echo 3. Get bot token from @BotFather on Telegram
        echo.
        echo Press Enter after configuring .env file...
        pause
    ) else (
        echo [ERROR] .env.template not found. Please create environment configuration.
        pause
        exit /b 1
    )
)

echo [INFO] Starting development environment...
echo.

REM Start development environment
call scripts\dev-setup.bat setup

echo.
echo ========================================
echo  🚀 Quick Actions
echo ========================================
echo.
echo [1] View Application Logs (Real-time)
echo [2] View All Services Logs
echo [3] Open Health Check (Browser)
echo [4] Check Service Status
echo [5] Restart Bot Service
echo [6] Stop All Services
echo [0] Exit
echo.

:menu
set /p action="Choose an action (0-6): "

if "%action%"=="1" (
    start "" dev-logs.bat
    goto :menu
)

if "%action%"=="2" (
    start "" all-logs.bat
    goto :menu
)

if "%action%"=="3" (
    echo [INFO] Opening health check in browser...
    start "" http://localhost:9080/actuator/health
    goto :menu
)

if "%action%"=="4" (
    echo.
    echo ========================================
    echo  Service Status
    echo ========================================
    docker-compose -f docker-compose.dev.yml ps
    echo.
    echo Service URLs:
    echo • Application: http://localhost:9080
    echo • Health Check: http://localhost:9080/actuator/health
    echo • Metrics: http://localhost:9080/actuator/prometheus
    echo • Debug Port: localhost:5005
    echo.
    pause
    goto :menu
)

if "%action%"=="5" (
    echo [INFO] Restarting bot service...
    docker-compose -f docker-compose.dev.yml restart nse-bot
    echo [SUCCESS] Bot service restarted
    pause
    goto :menu
)

if "%action%"=="6" (
    echo [INFO] Stopping all services...
    docker-compose -f docker-compose.dev.yml down
    echo [SUCCESS] All services stopped
    pause
    goto :end
)

if "%action%"=="0" goto :end

echo [ERROR] Invalid choice. Please select 0-6.
goto :menu

:end
echo.
echo [INFO] Quick Start session ended
echo Use 'quick-start.bat' anytime to manage your development environment
pause
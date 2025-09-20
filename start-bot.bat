@echo off
echo =================================================
echo   NSE Stock Analysis Bot - Starting...
echo =================================================
echo.

REM Check if environment variables are set
if "%TELEGRAM_BOT_TOKEN%"=="" (
    echo ERROR: TELEGRAM_BOT_TOKEN environment variable is not set!
    echo.
    echo Please set your bot token:
    echo set TELEGRAM_BOT_TOKEN=your_bot_token_here
    echo.
    echo Or create application-local.yml with your configuration
    echo.
    pause
    exit /b 1
)

echo Bot Token: %TELEGRAM_BOT_TOKEN:~0,10%...
echo.
echo Building and starting the application...
echo.

mvn spring-boot:run -Dspring-boot.run.profiles=local

pause
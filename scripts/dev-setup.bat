@echo off
REM NSE Stock Analysis Bot - Windows Development Environment Setup Script

setlocal enabledelayedexpansion

REM Configuration
set PROJECT_NAME=nse-stock-analysis-bot
set COMPOSE_FILE=docker-compose.dev.yml
set ENV_FILE=.env
set DEV_ENV_FILE=.env.development

title NSE Bot - Development Setup

echo.
echo ========================================
echo  NSE Stock Analysis Bot - Dev Setup
echo ========================================
echo.

REM Check prerequisites
echo [INFO] Checking prerequisites...

REM Check if Docker is installed and running
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker is not installed. Please install Docker Desktop first.
    pause
    exit /b 1
)

docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker daemon is not running. Please start Docker Desktop.
    pause
    exit /b 1
)

REM Check if Docker Compose is installed
docker-compose --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker Compose is not installed. Please install Docker Compose.
    pause
    exit /b 1
)

echo [SUCCESS] Prerequisites check completed

REM Setup environment file
echo [INFO] Setting up environment file...

if not exist "%ENV_FILE%" (
    if exist "%DEV_ENV_FILE%" (
        echo [INFO] Copying development environment template...
        copy "%DEV_ENV_FILE%" "%ENV_FILE%" >nul
    ) else (
        echo [ERROR] Neither %ENV_FILE% nor %DEV_ENV_FILE% found
        pause
        exit /b 1
    )
)

REM Check if bot token is configured
findstr /c:"your_dev_bot_token_here" "%ENV_FILE%" >nul 2>&1
if %errorlevel% equ 0 (
    echo [WARNING] Please configure your Telegram bot token in %ENV_FILE%
    echo.
    echo To get a bot token:
    echo 1. Message @BotFather on Telegram
    echo 2. Create a new bot with /newbot
    echo 3. Copy the token to TELEGRAM_BOT_TOKEN in %ENV_FILE%
    echo.
    pause
)

echo [SUCCESS] Environment file is ready

REM Handle command line arguments
if "%1"=="" goto :setup
if "%1"=="setup" goto :setup
if "%1"=="start" goto :start
if "%1"=="stop" goto :stop
if "%1"=="restart" goto :restart
if "%1"=="logs" goto :logs
if "%1"=="health" goto :health
if "%1"=="status" goto :status
if "%1"=="build" goto :build
if "%1"=="clean" goto :clean
goto :usage

:setup
echo [INFO] Building development environment...

REM Stop any existing containers
docker-compose -f %COMPOSE_FILE% down 2>nul

REM Build application with development profile
docker-compose -f %COMPOSE_FILE% build --no-cache
if %errorlevel% neq 0 (
    echo [ERROR] Failed to build development environment
    pause
    exit /b 1
)

echo [SUCCESS] Development environment built

:start
echo [INFO] Starting development services...

REM Start database and cache first
docker-compose -f %COMPOSE_FILE% up -d postgres redis
if %errorlevel% neq 0 (
    echo [ERROR] Failed to start database and Redis
    pause
    exit /b 1
)

REM Wait for database to be ready
echo [INFO] Waiting for database to be ready...
set /a count=0
:wait_db
docker-compose -f %COMPOSE_FILE% exec postgres pg_isready -U nse_bot >nul 2>&1
if %errorlevel% equ 0 goto :db_ready
set /a count+=1
if %count% gtr 30 (
    echo [ERROR] Database failed to start within timeout
    pause
    exit /b 1
)
timeout /t 2 /nobreak >nul
goto :wait_db

:db_ready
REM Wait for Redis to be ready
echo [INFO] Waiting for Redis to be ready...
set /a count=0
:wait_redis
docker-compose -f %COMPOSE_FILE% exec redis redis-cli ping >nul 2>&1
if %errorlevel% equ 0 goto :redis_ready
set /a count+=1
if %count% gtr 20 (
    echo [ERROR] Redis failed to start within timeout
    pause
    exit /b 1
)
timeout /t 1 /nobreak >nul
goto :wait_redis

:redis_ready
REM Start the application
docker-compose -f %COMPOSE_FILE% up -d nse-bot
if %errorlevel% neq 0 (
    echo [ERROR] Failed to start NSE Bot application
    pause
    exit /b 1
)

REM Wait for application to be ready
echo [INFO] Waiting for application to be ready...
set /a count=0
:wait_app
curl -f http://localhost:9080/actuator/health >nul 2>&1
if %errorlevel% equ 0 goto :app_ready
set /a count+=1
if %count% gtr 60 (
    echo [ERROR] Application failed to start within timeout
    pause
    exit /b 1
)
timeout /t 5 /nobreak >nul
goto :wait_app

:app_ready
echo [SUCCESS] Development services started successfully

:health
echo [INFO] Running health checks...

REM Check application health
curl -s http://localhost:8080/actuator/health | findstr "UP" >nul
if %errorlevel% equ 0 (
    echo [SUCCESS] Application health check passed
) else (
    echo [ERROR] Application health check failed
    goto :show_status
)

REM Check database connectivity
docker-compose -f %COMPOSE_FILE% exec postgres pg_isready -U nse_bot >nul 2>&1
if %errorlevel% equ 0 (
    echo [SUCCESS] Database health check passed
) else (
    echo [ERROR] Database health check failed
    goto :show_status
)

REM Check Redis connectivity
docker-compose -f %COMPOSE_FILE% exec redis redis-cli ping >nul 2>&1
if %errorlevel% equ 0 (
    echo [SUCCESS] Redis health check passed
) else (
    echo [ERROR] Redis health check failed
    goto :show_status
)

:show_status
echo.
echo ========================================
echo  Development Environment Status
echo ========================================
docker-compose -f %COMPOSE_FILE% ps
echo.

echo [INFO] Service URLs:
echo Application: http://localhost:9080
echo Health Check: http://localhost:9080/actuator/health
echo Metrics: http://localhost:9080/actuator/prometheus
echo Debug Port: localhost:5005 (for remote debugging^)
echo.
echo Database: localhost:5433 (nse_bot_dev/nse_bot/dev_password^)
echo Redis: localhost:6380 (password: dev_redis_password^)
echo.

echo [INFO] Development Commands:
echo View logs: dev-logs.bat
echo Stop services: dev-setup.bat stop
echo Restart app: dev-setup.bat restart
echo Health check: dev-setup.bat health
echo Clean environment: dev-setup.bat clean
echo.

echo [INFO] IDE Debugging Setup:
echo Host: localhost
echo Port: 5005
echo Transport: Socket
echo Debugger mode: Attach
echo.

echo [SUCCESS] Development environment setup completed!
echo Your bot is now running and ready for development.
echo Use 'dev-logs.bat' to view real-time logs.

if "%1"=="setup" pause
goto :end

:stop
echo [INFO] Stopping development environment...
docker-compose -f %COMPOSE_FILE% down
echo [SUCCESS] Development environment stopped
if "%1"=="stop" pause
goto :end

:restart
echo [INFO] Restarting services...
if "%2" neq "" (
    docker-compose -f %COMPOSE_FILE% restart %2
    echo [SUCCESS] Service %2 restarted
) else (
    docker-compose -f %COMPOSE_FILE% restart nse-bot
    echo [SUCCESS] NSE Bot service restarted
)
if "%1"=="restart" pause
goto :end

:logs
echo [INFO] Showing logs...
if "%2" neq "" (
    docker-compose -f %COMPOSE_FILE% logs -f %2
) else (
    docker-compose -f %COMPOSE_FILE% logs -f nse-bot
)
goto :end

:status
goto :show_status

:build
echo [INFO] Rebuilding development environment...
docker-compose -f %COMPOSE_FILE% down
docker-compose -f %COMPOSE_FILE% build --no-cache
echo [SUCCESS] Development environment rebuilt
if "%1"=="build" pause
goto :end

:clean
echo [INFO] Cleaning development environment...
docker-compose -f %COMPOSE_FILE% down -v
docker system prune -f
echo [SUCCESS] Development environment cleaned
if "%1"=="clean" pause
goto :end

:usage
echo Usage: %0 {setup^|start^|stop^|restart^|logs^|health^|status^|build^|clean}
echo.
echo Commands:
echo   setup   - Full development environment setup (default^)
echo   start   - Start services and show status
echo   stop    - Stop all services
echo   restart - Restart services (optionally specify service^)
echo   logs    - Show logs (optionally specify service^)
echo   health  - Run health checks
echo   status  - Show service status and URLs
echo   build   - Rebuild application
echo   clean   - Stop and remove all containers and volumes
pause

:end
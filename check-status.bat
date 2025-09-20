@echo off
REM Simple status checker for NSE Bot development environment

title NSE Bot - Status Check

echo.
echo ========================================
echo  NSE Bot - Development Status Check
echo ========================================
echo.

REM Check Docker containers
echo [INFO] Checking Docker containers...
docker-compose -f docker-compose.dev.yml ps

echo.
echo [INFO] Checking application health...
curl -s http://localhost:9080/actuator/health 2>nul >nul
if %errorlevel% equ 0 (
    echo [SUCCESS] Application is responding
    echo.
    echo Health Check Response:
    curl -s http://localhost:9080/actuator/health
) else (
    echo [INFO] Application is still starting or not ready
)

echo.
echo [INFO] Service URLs:
echo - Application: http://localhost:9080
echo - Health Check: http://localhost:9080/actuator/health
echo - Database: localhost:5433
echo - Redis: localhost:6380
echo - Debug Port: localhost:5005

echo.
echo [INFO] To view logs: docker-compose -f docker-compose.dev.yml logs -f nse-bot
echo.

pause
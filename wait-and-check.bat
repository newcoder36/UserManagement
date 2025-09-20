@echo off
REM Wait for application startup and check health

title NSE Bot - Startup Monitor

echo.
echo ========================================
echo  NSE Bot - Waiting for Startup
echo ========================================
echo.

echo [INFO] Waiting for application to start...
echo [INFO] This may take 30-60 seconds for first startup

set /a count=0
:wait_loop
timeout /t 5 /nobreak >nul
set /a count+=1

REM Check if application is responding
curl -s http://localhost:9080/actuator/health >nul 2>&1
if %errorlevel% equ 0 (
    goto :success
)

if %count% geq 20 (
    echo [TIMEOUT] Application failed to start within 100 seconds
    echo [INFO] Checking logs...
    docker-compose -f docker-compose.dev.yml logs --tail=20 nse-bot
    goto :end
)

echo Attempt %count%/20 - Still waiting...
goto :wait_loop

:success
echo.
echo [SUCCESS] Application is ready!
echo.
curl -s http://localhost:9080/actuator/health
echo.
echo.
echo [INFO] Application URLs:
echo - Health Check: http://localhost:9080/actuator/health
echo - Application: http://localhost:9080
echo - Debug Port: localhost:5005
echo.

:end
pause
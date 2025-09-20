@echo off
REM Fix port conflicts for NSE Bot development environment

title NSE Bot - Port Conflict Resolver

echo.
echo ========================================
echo  NSE Bot - Port Conflict Resolver
echo ========================================
echo.

echo [INFO] Checking for port conflicts...
echo.

REM Check which services are using common ports
echo Checking port 5432 (PostgreSQL):
netstat -an | findstr :5432 >nul
if %errorlevel% equ 0 (
    echo [CONFLICT] Port 5432 is in use
    netstat -an | findstr :5432
) else (
    echo [OK] Port 5432 is available
)

echo.
echo Checking port 6379 (Redis):
netstat -an | findstr :6379 >nul
if %errorlevel% equ 0 (
    echo [CONFLICT] Port 6379 is in use
    netstat -an | findstr :6379
) else (
    echo [OK] Port 6379 is available
)

echo.
echo Checking port 8080 (Application):
netstat -an | findstr :8080 >nul
if %errorlevel% equ 0 (
    echo [CONFLICT] Port 8080 is in use
    netstat -an | findstr :8080
) else (
    echo [OK] Port 8080 is available
)

echo.
echo ========================================
echo  Solution Options
echo ========================================
echo.
echo [1] Stop existing PostgreSQL service (if running locally)
echo [2] Use different ports for development
echo [3] Stop all Docker containers using these ports
echo [4] Show detailed port usage
echo [5] Exit
echo.

set /p choice="Choose a solution (1-5): "

if "%choice%"=="1" goto :stop_postgres
if "%choice%"=="2" goto :change_ports
if "%choice%"=="3" goto :stop_docker
if "%choice%"=="4" goto :show_details
if "%choice%"=="5" goto :end
goto :menu

:stop_postgres
echo.
echo [INFO] Attempting to stop local PostgreSQL service...
net stop postgresql-x64-13 2>nul
net stop postgresql-x64-14 2>nul
net stop postgresql-x64-15 2>nul
net stop postgresql-x64-16 2>nul
sc stop postgresql 2>nul
echo [INFO] PostgreSQL services stop attempted
echo [INFO] Try running dev-setup.bat again
pause
goto :end

:change_ports
echo.
echo [INFO] Creating alternative port configuration...
echo [INFO] This will use different ports to avoid conflicts:
echo - PostgreSQL: 5433 (instead of 5432)
echo - Redis: 6380 (instead of 6379) 
echo - Application: 8081 (instead of 8080)
echo.

REM Create alternative docker-compose file
copy docker-compose.dev.yml docker-compose.dev-alt.yml >nul

REM Update ports in the alternative file
powershell -Command "(Get-Content docker-compose.dev-alt.yml) -replace '5432:5432', '5433:5432' -replace '6379:6379', '6380:6379' -replace '8080:8080', '8081:8080' | Set-Content docker-compose.dev-alt.yml"

echo [SUCCESS] Created docker-compose.dev-alt.yml with alternative ports
echo [INFO] Run: docker-compose -f docker-compose.dev-alt.yml up -d
echo [INFO] Your application will be available at: http://localhost:8081
pause
goto :end

:stop_docker
echo.
echo [INFO] Stopping all Docker containers that might be using these ports...
docker ps -q --filter "publish=5432" | xargs -r docker stop 2>nul
docker ps -q --filter "publish=6379" | xargs -r docker stop 2>nul
docker ps -q --filter "publish=8080" | xargs -r docker stop 2>nul

REM Also try to stop by common container names
docker stop postgres postgresql redis nse-bot nse-bot-postgres nse-bot-redis nse-bot-app 2>nul

echo [SUCCESS] Docker containers stopped
echo [INFO] Try running dev-setup.bat again
pause
goto :end

:show_details
echo.
echo [INFO] Detailed port usage:
echo.
echo === Port 5432 (PostgreSQL) ===
netstat -ano | findstr :5432
echo.
echo === Port 6379 (Redis) ===
netstat -ano | findstr :6379
echo.
echo === Port 8080 (Application) ===
netstat -ano | findstr :8080
echo.
echo === All Docker containers ===
docker ps -a
echo.
pause
goto :menu

:menu
goto :choice

:end
echo.
echo [INFO] Port conflict resolver finished
@echo off
REM ============================================================
REM Home Rental Application — start all services in order
REM
REM Boots Docker infrastructure first, then the 3 infrastructure
REM Spring Boot services (config-server, eureka, gateway), then
REM every business service, then the frontend + ngrok.
REM
REM Each service gets its own cmd window with a HRA-<name> title
REM so you can tell them apart in the taskbar AND so stop-all.bat
REM can shut them down without nuking IntelliJ or other JVMs.
REM ============================================================

setlocal
set REPO=C:\Siva\Microservices\Home-Rental-Application

echo.
echo ============================================================
echo  Step 1/7  Docker infrastructure (Kafka, MongoDB, Oracle...)
echo ============================================================
REM Bring up ONLY the infrastructure containers — explicitly named
REM to skip the Spring Boot service containers that docker-compose.yml
REM also defines. Those service containers run `mvn package` inside
REM Docker as a build step, which is (a) slow, (b) duplicate of what
REM this script does on the host with `mvn spring-boot:run`, and
REM (c) was failing for api-gateway with "exit code 1" because the
REM in-container Maven build couldn't resolve something with -q
REM hiding the actual error. Skipping the service containers
REM sidesteps that entire build path.
cd /d "%REPO%"
docker compose up -d oracle-db mongodb zookeeper kafka
if errorlevel 1 (
    echo.
    echo ERROR: docker compose up failed.
    echo  - If you see "Cannot connect to the Docker daemon": Docker Desktop
    echo    isn't running. Open it, wait for the whale icon to go solid, retry.
    echo  - If you see a port conflict on 1521 / 27017 / 9093 / 2181: another
    echo    container or local DB is using that port. Stop the conflicting
    echo    process and retry.
    pause
    exit /b 1
)
echo Waiting 20s for Kafka / DB to be ready...
timeout /t 20 /nobreak >nul

echo.
echo ============================================================
echo  Step 2/7  config-server on port 8888 (MUST be first)
echo ============================================================
REM All other Spring Boot services read centralised config from
REM config-server at boot. Starting them before this would make
REM them fall back to their local application.yaml (which works
REM in dev but isn't what we want).
start "HRA-config-server" cmd /k "cd /d %REPO%\config-server && mvn spring-boot:run"
echo Waiting 25s for config-server to be reachable on :8888 ...
timeout /t 25 /nobreak >nul

echo.
echo ============================================================
echo  Step 3/7  Service-Registry / Eureka on port 8761
echo ============================================================
start "HRA-eureka" cmd /k "cd /d %REPO%\Service-Registry && mvn spring-boot:run"
echo Waiting 15s for Eureka...
timeout /t 15 /nobreak >nul

echo.
echo ============================================================
echo  Step 4/7  api-gateway on port 8080
echo ============================================================
start "HRA-api-gateway" cmd /k "cd /d %REPO%\api-gateway && mvn spring-boot:run"
echo Waiting 15s for the gateway to register with Eureka...
timeout /t 15 /nobreak >nul

echo.
echo ============================================================
echo  Step 5/7  Business services (parallel)
echo ============================================================
REM These all depend on config-server + Eureka + gateway being up
REM but are independent of each other, so we kick them off in
REM parallel. Each gets a distinct window title.
start "HRA-auth-service"         cmd /k "cd /d %REPO%\auth-service          && mvn spring-boot:run"
start "HRA-user-service"         cmd /k "cd /d %REPO%\user-service          && mvn spring-boot:run"
start "HRA-property-service"     cmd /k "cd /d %REPO%\property-service      && mvn spring-boot:run"
start "HRA-notification-service" cmd /k "cd /d %REPO%\notification-service  && mvn spring-boot:run"
start "HRA-payment-service"      cmd /k "cd /d %REPO%\payment-service       && mvn spring-boot:run"
start "HRA-maintenance-service"  cmd /k "cd /d %REPO%\maintenance-service   && mvn spring-boot:run"
start "HRA-document-service"     cmd /k "cd /d %REPO%\DocumentationService  && mvn spring-boot:run"
start "HRA-lease-service"        cmd /k "cd /d %REPO%\LeaseService          && mvn spring-boot:run"
start "HRA-review-service"       cmd /k "cd /d %REPO%\ReviewService         && mvn spring-boot:run"
start "HRA-compliance-service"   cmd /k "cd /d %REPO%\ComplanceService      && mvn spring-boot:run"
start "HRA-kyc-service"          cmd /k "cd /d %REPO%\KYCService            && mvn spring-boot:run"
start "HRA-analytics-service"    cmd /k "cd /d %REPO%\analytics-service     && mvn spring-boot:run"
echo Waiting 30s for business services to register with Eureka...
timeout /t 30 /nobreak >nul

echo.
echo ============================================================
echo  Step 6/7  Frontend (Vite dev server on port 4200)
echo ============================================================
start "HRA-frontend" cmd /k "cd /d %REPO%\frontend && npm run dev"
timeout /t 10 /nobreak >nul

echo.
echo ============================================================
echo  Step 7/7  ngrok tunnel (exposes frontend at port 4200)
echo ============================================================
start "HRA-ngrok" cmd /k "ngrok http 4200"

echo.
echo ============================================================
echo  ALL SERVICES STARTED
echo ============================================================
echo.
echo Each service runs in its own window titled HRA-^<name^>.
echo Use scripts\stop-all.bat to shut everything down cleanly.
echo.
echo  Useful URLs:
echo    Frontend:        http://localhost:4200
echo    API Gateway:     http://localhost:8080
echo    Eureka:          http://localhost:8761
echo    Config Server:   http://localhost:8888
echo    ngrok dashboard: http://localhost:4040 (check the ngrok window for the public URL)
echo.
echo Tip: wait ~60 seconds after this script finishes before
echo trying the app — late-starting services (gateway routes,
echo Kafka consumers) need that long to fully register.
echo.
pause
endlocal

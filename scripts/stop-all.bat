@echo off
REM ============================================================
REM Home Rental Application — stop everything started by start-all.bat
REM
REM Closes the HRA-<name> cmd windows (and their child JVMs)
REM by window title, so IntelliJ and any other unrelated Java
REM processes on the machine are left alone.
REM
REM Falls back to image-name kills for ngrok (since the ngrok
REM window is named HRA-ngrok but the actual ngrok.exe runs as
REM a child process) — and for orphan Vite processes if the
REM HRA-frontend window was closed manually.
REM ============================================================

setlocal

echo.
echo ============================================================
echo  STOPPING ALL HRA SERVICES
echo ============================================================

echo.
echo ------------------------------------------------------------
echo Closing HRA-^<service^> windows + their JVM children...
echo ------------------------------------------------------------
REM /T = also kill child processes (so the java.exe spawned by
REM the maven wrapper inside the cmd window dies with it).
REM 2^>nul suppresses "process not found" noise for windows that
REM were never started or were already closed.
for %%S in (
    HRA-config-server
    HRA-eureka
    HRA-api-gateway
    HRA-auth-service
    HRA-user-service
    HRA-property-service
    HRA-notification-service
    HRA-payment-service
    HRA-maintenance-service
    HRA-document-service
    HRA-lease-service
    HRA-review-service
    HRA-compliance-service
    HRA-kyc-service
    HRA-analytics-service
    HRA-frontend
    HRA-ngrok
) do (
    echo   stopping %%S
    taskkill /F /FI "WINDOWTITLE eq %%S" /T 2>nul
)

echo.
echo ------------------------------------------------------------
echo Catching orphan ngrok / Vite processes (in case a window
echo was closed manually before this script ran)...
echo ------------------------------------------------------------
REM ngrok always has the exe name "ngrok.exe", safe to kill globally.
taskkill /F /IM ngrok.exe 2>nul

REM For node.exe we DON'T want to kill every node process on the
REM machine (could be a VSCode extension host, a build server,
REM etc.). Match only processes whose command line contains "vite"
REM — that's the Vite dev server spawned by `npm run dev`.
wmic process where "name='node.exe' and CommandLine like '%%vite%%'" call terminate >nul 2>&1

echo.
echo ------------------------------------------------------------
echo Stopping Docker containers (Kafka, MongoDB, Oracle...)
echo ------------------------------------------------------------
cd /d C:\Siva\Microservices\Home-Rental-Application
docker compose down

echo.
echo ============================================================
echo  ALL HRA SERVICES STOPPED
echo ============================================================
echo.
echo Note: IntelliJ IDEA and any other Java processes you started
echo manually were NOT touched. If you launched a service from
echo IntelliJ's green play button, stop it from IntelliJ (red square).
echo.
pause
endlocal

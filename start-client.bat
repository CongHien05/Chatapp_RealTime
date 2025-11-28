@echo off
echo ========================================
echo Starting Metus Chat Client
echo ========================================
echo.
echo Make sure:
echo 1. Server is running on another machine
echo 2. config.properties has correct client.rmi.registry IP
echo.
pause
echo.
echo Compiling...
call mvn clean compile -q -DskipTests
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Compilation failed!
    pause
    exit /b 1
)
echo.
echo Starting client...
echo.
call mvn javafx:run
pause


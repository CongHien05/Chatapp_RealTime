@echo off
echo ========================================
echo Starting Metus Chat Server
echo ========================================
echo.
echo Make sure:
echo 1. MySQL/XAMPP is running
echo 2. config.properties has correct server.host IP
echo 3. Firewall allows port 1099, 8888, 9999
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
echo Starting server...
echo Press Ctrl+C to stop
echo.
call mvn exec:java -Dexec.mainClass="org.example.danbainoso.server.ServerMain"
pause


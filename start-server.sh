#!/bin/bash

echo "========================================"
echo "Starting Metus Chat Server"
echo "========================================"
echo ""
echo "Make sure:"
echo "1. MySQL is running"
echo "2. config.properties has correct server.host IP"
echo "3. Firewall allows port 1099, 8888, 9999"
echo ""
read -p "Press Enter to continue..."
echo ""
echo "Compiling..."
mvn clean compile -q -DskipTests
if [ $? -ne 0 ]; then
    echo ""
    echo "[ERROR] Compilation failed!"
    exit 1
fi
echo ""
echo "Starting server..."
echo "Press Ctrl+C to stop"
echo ""
mvn exec:java -Dexec.mainClass="org.example.danbainoso.server.ServerMain"


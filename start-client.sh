#!/bin/bash

echo "========================================"
echo "Starting Metus Chat Client"
echo "========================================"
echo ""
echo "Make sure:"
echo "1. Server is running on another machine"
echo "2. config.properties has correct client.rmi.registry IP"
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
echo "Starting client..."
echo ""
mvn javafx:run


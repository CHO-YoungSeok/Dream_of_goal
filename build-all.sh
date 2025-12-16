#!/bin/bash

echo "=========================================="
echo "Building All JAR Files"
echo "=========================================="
echo ""

# Client 빌드
./build-client.sh
if [ $? -ne 0 ]; then
    echo "❌ Client build failed!"
    exit 1
fi

echo ""

# Server 빌드
./build-server.sh
if [ $? -ne 0 ]; then
    echo "❌ Server build failed!"
    exit 1
fi

echo ""
echo "=========================================="
echo "✅ All builds completed successfully!"
echo "=========================================="
echo "Generated files:"
echo "  - baseball-client.jar"
echo "  - baseball-server.jar"
echo "To run the server:"
echo "  java -jar baseball-server.jar"
echo "To run the client:"
echo "  java -jar baseball-client.jar"
echo "=========================================="

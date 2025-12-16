#!/bin/bash

echo "=========================================="
echo "Building Baseball Game Server JAR"
echo "=========================================="

# 디렉토리 정리
echo "Cleaning previous build..."
rm -rf build/server
mkdir -p build/server

# 소스 파일 컴파일
echo "Compiling source files..."
javac -d build/server \
    -encoding UTF-8 \
    src/common/*.java \
    src/server/*.java

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed!"
    exit 1
fi

echo "✓ Compilation successful"

# JAR 파일 생성
echo "Creating JAR file..."
cd build/server
jar cvfe ../../baseball-server.jar server.BaseballServerGUI . > /dev/null 2>&1
cd ../..

if [ $? -ne 0 ]; then
    echo "❌ JAR creation failed!"
    exit 1
fi

echo "=========================================="
echo "✅ Build completed successfully!"
echo "=========================================="
echo "Output: baseball-server.jar"
echo ""
echo "To run the server:"
echo "  java -jar baseball-server.jar"
echo "=========================================="

#!/bin/bash

echo "=========================================="
echo "Building Baseball Game Client JAR"
echo "=========================================="

# 디렉토리 정리
echo "Cleaning previous build..."
rm -rf build/client
mkdir -p build/client

# 소스 파일 컴파일
echo "Compiling source files..."
javac -d build/client \
    -encoding UTF-8 \
    src/common/*.java \
    src/client/*.java \
    src/client/network/*.java \
    src/client/state/*.java \
    src/client/ui/*.java \
    src/client/util/*.java

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed!"
    exit 1
fi

echo "✓ Compilation successful"

# 이미지 리소스 복사
echo "Copying resources..."
if [ -d "src/image" ]; then
    cp -r src/image build/client/
    echo "✓ Resources copied"
fi

# JAR 파일 생성
echo "Creating JAR file..."
cd build/client
jar cvfe ../../baseball-client.jar client.BaseballClientGUI . > /dev/null 2>&1
cd ../..

if [ $? -ne 0 ]; then
    echo "❌ JAR creation failed!"
    exit 1
fi

echo "=========================================="
echo "✅ Build completed successfully!"
echo "=========================================="
echo "Output: baseball-client.jar"
echo ""
echo "To run the client:"
echo "  java -jar baseball-client.jar"
echo "=========================================="

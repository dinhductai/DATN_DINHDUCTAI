#!/bin/bash

echo "========================================="
echo "Building Smart Schedule Microservices"
echo "========================================="

# Build common-exception-lib first
echo "Step 1: Building common-exception-lib..."
cd common-exception-lib
mvn clean install -DskipTests
if [ $? -ne 0 ]; then
    echo "Failed to build common-exception-lib"
    exit 1
fi
cd ..

# Build all services
services=("discovery-server" "api-gateway" "auth-service" "user-service" "task-service" "ai-service" "product-service" "email-service")

for service in "${services[@]}"
do
    echo "Building $service..."
    cd $service/$service
    mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "Failed to build $service"
        exit 1
    fi
    cd ../..
done

echo "========================================="
echo "Build completed successfully!"
echo "Now you can run: docker-compose up -d"
echo "========================================="

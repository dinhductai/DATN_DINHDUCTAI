@echo off
echo =========================================
echo Building Smart Schedule Microservices
echo =========================================

echo Step 1: Building common-exception-lib...
cd common-exception-lib
call mvn clean install -DskipTests
if %errorlevel% neq 0 (
    echo Failed to build common-exception-lib
    exit /b 1
)
cd ..

echo Building discovery-server...
cd discovery-server\discovery-server
call mvn clean package -DskipTests
if %errorlevel% neq 0 exit /b 1
cd ..\..

echo Building api-gateway...
cd api-gateway\api-gateway
call mvn clean package -DskipTests
if %errorlevel% neq 0 exit /b 1
cd ..\..

echo Building auth-service...
cd auth-service\auth-service
call mvn clean package -DskipTests
if %errorlevel% neq 0 exit /b 1
cd ..\..

echo Building user-service...
cd user-service\user-service
call mvn clean package -DskipTests
if %errorlevel% neq 0 exit /b 1
cd ..\..

echo Building task-service...
cd task-service\task-service
call mvn clean package -DskipTests
if %errorlevel% neq 0 exit /b 1
cd ..\..

echo Building ai-service...
cd ai-service\ai-service
call mvn clean package -DskipTests
if %errorlevel% neq 0 exit /b 1
cd ..\..

echo Building product-service...
cd product-service\product-service
call mvn clean package -DskipTests
if %errorlevel% neq 0 exit /b 1
cd ..\..

echo Building order-service...
cd order-service\order-service
call mvn clean package -DskipTests
if %errorlevel% neq 0 exit /b 1
cd ..\..

echo =========================================
echo Build completed successfully!
echo Now you can run: docker-compose up -d
echo =========================================

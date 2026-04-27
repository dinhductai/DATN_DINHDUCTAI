# ════════════════════════════════════════════════════════════
# STAGE 1: Build với Maven
# ════════════════════════════════════════════════════════════
FROM maven:3.9.9-eclipse-temurin-21 AS build

# Set working directory
WORKDIR /app

# Copy pom.xml và source code
COPY pom.xml .
COPY src ./src

# Build JAR file (skip tests để build nhanh)
RUN mvn clean package -DskipTests

# ════════════════════════════════════════════════════════════
# STAGE 2: Runtime với JRE (nhẹ hơn JDK)
# ════════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jre-alpine

# Install curl cho healthcheck (Alpine dùng apk thay vì apt-get)
RUN apk add --no-cache curl

# Tạo non-root user (bảo mật)
RUN addgroup -S spring && adduser -S spring -G spring

# Set working directory
WORKDIR /app

# Copy JAR file từ build stage (chỉ ~50MB)
COPY --from=build /app/target/*.jar app.jar

# Change ownership sang user spring
RUN chown spring:spring app.jar

# Switch sang non-root user
USER spring

# Expose port (thay số port tương ứng mỗi service)
EXPOSE 9001

# Health check (optional nhưng nên có)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:9001/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "app.jar"]



# FROM openjdk:21-jdk-slim
# RUN apt-get update && apt-get install -y curl
# ARG JAR_FILE=target/*.jar
# COPY ${JAR_FILE} app.jar
# ENTRYPOINT ["java","-jar","/app.jar"]

# ════════════════════════════════════════════════════════════
# STAGE 1: Build với Maven
# ════════════════════════════════════════════════════════════
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy root pom for multi-module resolution
COPY pom.xml .

# Install common-exception-lib first (internal dependency)
COPY common-exception-lib/pom.xml ./common-exception-lib/
COPY common-exception-lib/src ./common-exception-lib/src
RUN cd common-exception-lib && mvn clean install -DskipTests

# Copy service pom and source
COPY task-service/task-service/pom.xml ./task-service/
COPY task-service/task-service/src ./task-service/src

RUN cd task-service && mvn clean package -DskipTests

# ════════════════════════════════════════════════════════════
# STAGE 2: Runtime với JRE
# ════════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

COPY --from=build /app/task-service/target/*.jar app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 9007

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:9007/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "app.jar"]

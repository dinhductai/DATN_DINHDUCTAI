# ════════════════════════════════════════════════════════════
# STAGE 1: Build với Maven (no internal deps needed)
# ════════════════════════════════════════════════════════════
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY discovery-server/discovery-server/pom.xml ./discovery-server/
COPY discovery-server/discovery-server/src ./discovery-server/src

RUN cd discovery-server && mvn clean package -DskipTests

# ════════════════════════════════════════════════════════════
# STAGE 2: Runtime với JRE
# ════════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

COPY --from=build /app/discovery-server/target/*.jar app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 8761

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8761/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "app.jar"]

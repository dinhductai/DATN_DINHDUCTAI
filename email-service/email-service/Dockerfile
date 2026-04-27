# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY email-service/email-service/pom.xml ./email-service/
COPY common-exception-lib/pom.xml ./common-exception-lib/
COPY common-exception-lib/src ./common-exception-lib/src

RUN cd email-service && mvn dependency:go-offline

COPY email-service/email-service/src ./email-service/src
COPY common-exception-lib/src ./common-exception-lib/src

RUN cd email-service && mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/email-service/target/*.jar app.jar

EXPOSE 9002

ENTRYPOINT ["java", "-jar", "app.jar"]

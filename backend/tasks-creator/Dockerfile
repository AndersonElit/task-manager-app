# Stage 1 — build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn package -DskipTests --no-transfer-progress

# Stage 2 — runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/infrastructure/entry-points/app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

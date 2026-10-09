# Build on CI, never on the 2 GB application Droplet. Keep root Dockerfile for Railway.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system ats && useradd --system --gid ats ats
COPY --from=build /app/target/ats-*.jar /app/app.jar
USER ats
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

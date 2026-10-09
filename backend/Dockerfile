FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/target/twse-strategy-lab-api-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

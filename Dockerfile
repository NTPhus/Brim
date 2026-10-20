FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system brim && useradd --system --gid brim brim
RUN mkdir -p /app/data && chown brim:brim /app/data
COPY --from=build --chown=brim:brim /build/target/game-platform-1.0.0-SNAPSHOT.jar app.jar
USER brim
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

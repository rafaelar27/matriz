# Matriz: one image for the Spring Boot application (API + static user interface).
# The same image runs the web application and, with SPRING_PROFILES_ACTIVE=seed, the demo-data command.
# Configuration comes only from environment variables at run time (see .env.example); nothing secret is baked in.

# ---- Build stage: compile and package the jar (tests run in CI, not here) ----
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies first, so this layer is cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package \
    && cp target/matriz-*.jar /build/app.jar

# ---- Runtime stage: JRE only, non-root user ----
FROM eclipse-temurin:21-jre-jammy
# curl is used only by the Compose healthcheck (GET /config.js).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && apt-get clean
RUN groupadd --system --gid 10001 matriz \
    && useradd --system --uid 10001 --gid matriz --no-create-home --shell /usr/sbin/nologin matriz
WORKDIR /app
COPY --from=build --chown=matriz:matriz /build/app.jar /app/app.jar

USER matriz
# Default port; change it with MATRIZ_PORT (and publish that port).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

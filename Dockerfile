# Matriz: one image for the Spring Boot application (API + static user interface).
# The same image runs the web application and, with SPRING_PROFILES_ACTIVE=seed, the demo-data command.
# Configuration comes only from environment variables at run time (see .env.example); nothing secret is baked in.

# ---- Build stage: compile and package the jar (tests run in CI, not here) ----
# Base images are pinned by tag and by digest (the digest is what is actually used; the tag is for readers).
# Build stage image: Maven 3.9.11, Temurin JDK 21.0.9. Update tag and digest together.
FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build
WORKDIR /build

# Dependencies first, so this layer is cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package \
    && cp target/matriz-*.jar /build/app.jar

# ---- Runtime stage: JRE only, non-root user ----
# Temurin JRE 21.0.13 (tag and digest, see above).
FROM eclipse-temurin:21-jre-jammy@sha256:dff6eb46527bd293a3c06bbbe9e64e8c9f91f01905b5abbdad31761c03059d2c
# curl is used only by the Compose healthcheck (GET /config.js).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system --gid 10001 matriz \
    && useradd --system --uid 10001 --gid matriz --no-create-home --shell /usr/sbin/nologin matriz
WORKDIR /app
COPY --from=build --chown=matriz:matriz /build/app.jar /app/app.jar

USER matriz
# Default port; change it with MATRIZ_PORT (and publish that port).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

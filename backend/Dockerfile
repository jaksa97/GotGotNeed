# syntax=docker/dockerfile:1
#
# Builds the :backend Gradle module (Spring Boot / Kotlin) into a runnable
# container image.
#
# IMPORTANT: this Dockerfile lives in backend/, but must be built with the
# REPO ROOT as the build context (see docker-compose.yml's `build.context: .`
# + `build.dockerfile: backend/Dockerfile`), e.g.:
#
#   docker build -f backend/Dockerfile -t gotgotneed-backend .
#
# Why the whole repo? GotGotNeed is a single Gradle multi-module build
# (androidApp, iosApp, desktopApp, shared, webApp, backend — see
# settings.gradle.kts) that shares one Gradle wrapper. Building `:backend`
# still requires the wrapper, version catalog, and settings/build scripts to
# be present so Gradle can resolve the project graph, even though only the
# backend module's output is actually packaged into the final image.

# ---------------------------------------------------------------------------
# Stage 1: build the Spring Boot fat jar with the Gradle wrapper (JDK 17,
# matching the :backend module's Java toolchain in backend/build.gradle.kts).
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

# Whole monorepo is needed (see header comment); .dockerignore keeps this
# fast by excluding build/ output, .git, node_modules, IDE files, etc.
COPY . .

RUN chmod +x gradlew

# Cache Gradle's dependency/build cache across image builds (requires
# BuildKit, which is the default builder for modern Docker/Compose).
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :backend:bootJar --no-daemon -x test

# bootJar produces backend-<version>.jar (the runnable fat jar) alongside a
# tiny backend-<version>-plain.jar (classes only, no dependencies) — copy
# only the runnable one, under a version-independent name.
RUN set -eu; \
    jar_file=$(find backend/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1); \
    test -n "$jar_file"; \
    cp "$jar_file" /app.jar

# ---------------------------------------------------------------------------
# Stage 2: minimal runtime image — JRE only, non-root user, nothing else
# from the build stage (no source, no Gradle cache, no other modules).
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime

RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring

WORKDIR /app
COPY --from=build /app.jar app.jar
RUN chown spring:spring app.jar

USER spring

EXPOSE 8080

# Lightweight TCP check (no curl/wget in the base image) so
# `docker compose ps` / `docker ps` report backend health without requiring
# an actuator dependency.
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=5 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

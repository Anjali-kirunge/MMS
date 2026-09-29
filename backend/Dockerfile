# Multi-stage build for the Military Asset Management System backend.
# Render builds this from the repository root context configured in render.yaml.

# ---- Stage 1: compile the jar -------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Resolve dependencies first so the layer is cached across code-only changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Stage 2: runtime ---------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# Run as a non-root user.
RUN groupadd --system --gid 1001 app \
 && useradd --system --uid 1001 --gid app --create-home app \
 && mkdir -p /app/logs \
 && chown -R app:app /app

COPY --from=build /build/target/military-asset-management-backend-1.0.0.jar /app/app.jar

USER app
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

# exec form so JAVA_OPTS expands and SIGTERM reaches the JVM for a clean shutdown.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

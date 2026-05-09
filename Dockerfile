# ─── Stage 1: Build ───────────────────────────────────────────────────────────
# WHY multi-stage:
#   The build stage has Maven + full JDK. The final image only needs JRE.
#   This keeps the deployed image lean (~200MB vs ~600MB with Maven included).

FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy POM first — Docker layer caches dependencies separately from source.
# If only source changes (not pom.xml), Maven won't re-download the internet.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    apk add --no-cache maven && \
    mvn dependency:go-offline -q

# Now copy source and build
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -q

# ─── Stage 2: Run ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Non-root user for security — Render recommends this
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/*.jar app.jar

# Render sets PORT env var. Spring Boot reads server.port.
# We expose 8080 as default but let Render override via PORT.
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
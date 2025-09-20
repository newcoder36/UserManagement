# Multi-stage Dockerfile for NSE Stock Analysis Bot
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS build

# Set working directory
WORKDIR /app

# Copy pom.xml first for better layer caching
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

# Install required packages and create non-root user
RUN apk add --no-cache \
    curl \
    tzdata \
    && addgroup -g 1001 -S appgroup \
    && adduser -u 1001 -S appuser -G appgroup

# Set timezone
ENV TZ=Asia/Kolkata

# Create app directory and set ownership
RUN mkdir -p /app /var/log/nse-bot /app/models \
    && chown -R appuser:appgroup /app /var/log/nse-bot

# Set working directory
WORKDIR /app

# Copy JAR from build stage
COPY --from=build /app/target/nse-stock-analysis-bot-*.jar app.jar

# Copy any ML models (if present) - models directory must exist but can be empty
COPY --chown=appuser:appgroup models/ /app/models/

# Create health check script
RUN echo '#!/bin/sh' > /app/healthcheck.sh \
    && echo 'curl -f http://localhost:8080/actuator/health || exit 1' >> /app/healthcheck.sh \
    && chmod +x /app/healthcheck.sh \
    && chown appuser:appgroup /app/healthcheck.sh

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD ["/app/healthcheck.sh"]

# JVM optimization arguments
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=80.0"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=prod"]

# Labels
LABEL maintainer="NSE Bot Team"
LABEL version="1.0.0"
LABEL description="NSE Stock Analysis Telegram Bot"
LABEL org.opencontainers.image.title="NSE Stock Analysis Bot"
LABEL org.opencontainers.image.description="Intelligent Telegram bot for NSE stock analysis and trading recommendations"
LABEL org.opencontainers.image.version="1.0.0"
LABEL org.opencontainers.image.source="https://github.com/your-org/nse-stock-analysis-bot"
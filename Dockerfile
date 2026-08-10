# syntax=docker/dockerfile:1.7

ARG TEMURIN_VERSION=21.0.11_10

FROM eclipse-temurin:${TEMURIN_VERSION}-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod 0755 mvnw && ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -ntp -DskipTests clean package

FROM eclipse-temurin:${TEMURIN_VERSION}-jre-alpine AS runtime
RUN apk upgrade --no-cache \
    && addgroup -S -g 10001 tizo \
    && adduser -S -D -H -u 10001 -G tizo tizo

WORKDIR /app
COPY --from=build --chown=tizo:tizo /workspace/target/tizo-api-*.jar /app/tizo-api.jar

ENV JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=/tmp -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=production

USER 10001:10001
EXPOSE 8080 8081

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=4 \
    CMD wget -q -T 2 -O /dev/null http://127.0.0.1:8081/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/tizo-api.jar"]

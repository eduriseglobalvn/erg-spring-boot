# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jdk AS jvm-builder

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre AS jvm-runtime

RUN apt-get update \
    && apt-get install --no-install-recommends -y ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 999 erg \
    && useradd --system --uid 999 --gid 999 --home-dir /app --shell /usr/sbin/nologin erg

WORKDIR /app
COPY --from=jvm-builder --chown=erg:erg /workspace/target/erg-spring-0.0.1-SNAPSHOT.jar /app/erg-spring.jar

USER erg
EXPOSE 1606

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=8 \
    CMD curl --fail --silent http://127.0.0.1:1606/ready >/dev/null || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/erg-spring.jar"]

FROM --platform=$TARGETPLATFORM ghcr.io/graalvm/native-image-community:25 AS native-builder

ARG TARGETARCH
ENV LANG=C \
    LC_ALL=C \
    NATIVE_IMAGE_OPTIONS=-march=compatibility \
    SPRING_PROFILES_ACTIVE=prod \
    MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,prometheus \
    MANAGEMENT_ENDPOINT_PROMETHEUS_ACCESS=read-only \
    MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED=true \
    FLYWAY_ENABLED=true \
    ERG_REDIS_STATE_ENABLED=true \
    ERG_REDIS_STATE_URL=redis://:native-build-placeholder@redis-state:6379 \
    ERG_REDIS_CACHE_ENABLED=true \
    ERG_REDIS_CACHE_URL=redis://:native-build-placeholder@redis-cache:6379 \
    SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration,org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration,org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,org.springframework.modulith.observability.autoconfigure.ModuleObservabilityAutoConfiguration,org.springframework.modulith.observability.autoconfigure.SpringDataRestModuleObservabilityAutoConfiguration,org.springframework.modulith.actuator.autoconfigure.ApplicationModulesEndpointConfiguration
WORKDIR /workspace

RUN test "$TARGETARCH" = "amd64"

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -Pnative -Dmaven.test.skip=true native:compile

FROM debian:bookworm-slim AS native-runtime

RUN apt-get update \
    && apt-get install --no-install-recommends -y ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 999 erg \
    && useradd --system --uid 999 --gid 999 --home-dir /app --shell /usr/sbin/nologin erg

WORKDIR /app
COPY --from=native-builder --chown=erg:erg /workspace/target/erg-spring-native /app/erg-spring
COPY --from=native-builder --chown=erg:erg /workspace/target/classes/db/migration/postgres/ /app/db/migration/postgres/

USER erg
EXPOSE 1606

HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=5 \
    CMD curl --fail --silent http://127.0.0.1:1606/ready >/dev/null || exit 1

ENTRYPOINT ["/app/erg-spring"]

# syntax=docker/dockerfile:1.7

FROM --platform=$TARGETPLATFORM ghcr.io/graalvm/native-image-community:25 AS native-builder

ARG TARGETARCH
ENV LANG=C \
    LC_ALL=C \
    NATIVE_IMAGE_OPTIONS=-march=compatibility \
    SPRING_PROFILES_ACTIVE=prod \
    FLYWAY_ENABLED=true \
    SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration,org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration
WORKDIR /workspace

RUN test "$TARGETARCH" = "amd64"

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -Pnative -Dmaven.test.skip=true native:compile

FROM debian:bookworm-slim AS runtime

RUN apt-get update \
    && apt-get install --no-install-recommends -y ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system erg \
    && useradd --system --gid erg --home-dir /app --shell /usr/sbin/nologin erg

WORKDIR /app
COPY --from=native-builder --chown=erg:erg /workspace/target/erg-spring /app/erg-spring
COPY --from=native-builder --chown=erg:erg /workspace/target/classes/db/migration/postgres/ /app/db/migration/postgres/

USER erg
EXPOSE 1606

HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=5 \
    CMD curl --fail --silent http://127.0.0.1:1606/ready >/dev/null || exit 1

ENTRYPOINT ["/app/erg-spring"]

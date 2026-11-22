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

# Password hashing calls the system libargon2 through the Foreign Function API (NativeArgon2PasswordEncoder).
RUN apt-get update \
    && apt-get install --no-install-recommends -y ca-certificates curl libargon2-1 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 999 erg \
    && useradd --system --uid 999 --gid 999 --home-dir /app --shell /usr/sbin/nologin erg

WORKDIR /app
COPY --from=jvm-builder --chown=erg:erg /workspace/target/erg-spring-0.0.1-SNAPSHOT.jar /app/erg-spring.jar
# compose.native.yml points FLYWAY_LOCATIONS at this path (migrations load from the filesystem, not the jar).
COPY --from=jvm-builder --chown=erg:erg /workspace/target/classes/db/migration/postgres/ /app/db/migration/postgres/

# Punch photos live on the tk-photos volume (compose.native.yml); the dir must exist owned by erg so the volume inherits it.
RUN install -d -o erg -g erg /var/lib/erg/tk-photos

USER erg
EXPOSE 1606

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=8 \
    CMD curl --fail --silent http://127.0.0.1:1606/ready >/dev/null || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseG1GC", "-XX:+ExitOnOutOfMemoryError", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/erg-spring.jar"]

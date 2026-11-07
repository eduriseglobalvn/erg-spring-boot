#!/usr/bin/env bash
# Deploy one commit to the ERG server. Run from a checkout:
#   git -c core.autocrlf=false archive <sha> | ssh erg@ssh.erg.edu.vn bash /opt/erg/ops/docker/deploy.sh <sha>
# (the script on the server is replaced by the commit's copy on every run)
# autocrlf=false matters on Windows: otherwise the archive carries CRLF and the shell scripts break.
# Steps: unpack source, build the JVM image, back up Postgres, rehearse the migrations and startup on a
# restored copy (isolated network, workers off), then switch the backend and nginx and wait for health.
# Migrations are forward-only: a failed rehearsal stops before production is touched.
set -Eeuo pipefail
SHA=${1:?usage: deploy.sh <git-sha> (source tar on stdin)}
ROOT=/opt/erg
DOCKER_DIR=$ROOT/ops/docker
ENV_FILE=$DOCKER_DIR/.env.production
IMAGE=erg-spring:$SHA-jvm
C="docker compose --env-file $ENV_FILE -f $DOCKER_DIR/compose.yml -f $DOCKER_DIR/compose.native.yml -f $DOCKER_DIR/compose.monitor.yml"
log() { echo "=== [$(date -u +%H:%M:%S)] $*"; }

log "unpack $SHA"
rm -rf "$ROOT/src" && mkdir -p "$ROOT/src" && tar xf - -C "$ROOT/src"
# Deploy files come from the commit; secrets (.env.production) stay on the server.
tar cf - -C "$ROOT/src/ops/docker" --exclude='.env.production' --exclude='.env.dev' . | tar xf - -C "$DOCKER_DIR"
# Prometheus, Grafana and Loki run as non-root users and must read their configs; only secrets are private.
chmod -R u=rwX,go=rX "$DOCKER_DIR" && chmod 600 "$ENV_FILE" && chmod 750 "$DOCKER_DIR/deploy.sh" && chmod -R o-rwx "$ROOT/src"
[ -f "$DOCKER_DIR/.env.dev" ] && chmod 600 "$DOCKER_DIR/.env.dev"
# Secrets the app needs but the repo must not hold: created once on a new server, never replaced
# (a new quiz-package key would stop verifying every package already signed). Back up .env.production with the data.
ensure_env() { # $1 name, $2 value used only when the name is missing or empty
  grep -q "^$1=." "$ENV_FILE" && return 0
  sed -i "/^$1=\$/d" "$ENV_FILE"
  [ -n "$(tail -c1 "$ENV_FILE")" ] && echo >> "$ENV_FILE"
  echo "$1=$2" >> "$ENV_FILE"; log "added $1 to .env.production"
}
ensure_env SECRET_QUIZ_PACKAGE_SIGNING_KEY "$(openssl rand -hex 32)"
ensure_env QUIZ_PACKAGE_PUBLIC_KEY_ID quiz-pkg-1
# The nightly cron runs ops/backup/backup.sh; its metrics dir is read by node-exporter (non-root).
install -d -m 700 "$ROOT/ops/backup" && install -m 750 "$ROOT/src/ops/backup/backup.sh" "$ROOT/ops/backup/backup.sh"
install -d -m 755 "$ROOT/ops/backup/metrics"

log "build $IMAGE"
DOCKER_BUILDKIT=1 nice -n 10 docker build -q --target jvm-runtime \
  --label org.opencontainers.image.revision="$SHA" -t "$IMAGE" "$ROOT/src" >/dev/null

log "back up postgres"
install -d -m 700 "$ROOT/backups"
DUMP=$ROOT/backups/erg-pre-$SHA-$(date -u +%Y%m%dT%H%M%SZ).dump
docker exec erg-postgres-1 pg_dump -U erg -d erg --format=custom > "$DUMP"
docker exec erg-postgres-1 pg_dumpall -U erg --roles-only --no-role-passwords > "$ROOT/backups/roles.sql"
chmod 600 "$DUMP" "$ROOT/backups/roles.sql"
find "$ROOT/backups" -name 'erg-pre-*.dump' -mtime +30 -delete

log "rehearse on a restored copy"
N=erg-rehearsal PW=$(openssl rand -hex 16)
cleanup() { docker rm -f $N-pg $N-redis $N-app >/dev/null 2>&1 || true; docker network rm $N >/dev/null 2>&1 || true; }
cleanup; trap cleanup EXIT
docker network create --internal $N >/dev/null
docker run -d --name $N-pg --network $N -e POSTGRES_USER=erg -e POSTGRES_PASSWORD="$PW" -e POSTGRES_DB=erg postgres:17-alpine >/dev/null
docker run -d --name $N-redis --network $N redis:8.8.0-alpine3.23 redis-server --requirepass "$PW" >/dev/null
until docker exec $N-pg pg_isready -U erg -d erg >/dev/null 2>&1; do sleep 1; done; sleep 2
docker exec -i $N-pg psql -q -U erg -d erg < "$ROOT/backups/roles.sql" 2>&1 | grep -v 'already exists' || true
docker exec -i $N-pg pg_restore -U erg -d erg --no-owner --role=erg < "$DUMP"
RUNTIME_USER=$(grep -E '^POSTGRES_RUNTIME_USER=' "$ENV_FILE" | cut -d= -f2-); RUNTIME_USER=${RUNTIME_USER:-erg_app}
docker exec $N-pg psql -q -U erg -d erg -c "alter role $RUNTIME_USER login password '$PW'"
R="redis://:$PW@$N-redis:6379"
docker run -d --name $N-app --network $N --env-file "$ENV_FILE" \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://$N-pg:5432/erg -e SPRING_DATASOURCE_USERNAME="$RUNTIME_USER" -e SPRING_DATASOURCE_PASSWORD="$PW" \
  -e SPRING_FLYWAY_USER=erg -e SPRING_FLYWAY_PASSWORD="$PW" -e FLYWAY_LOCATIONS=filesystem:/app/db/migration/postgres \
  -e SPRING_DATA_REDIS_URL="$R" -e ERG_REDIS_STATE_URL="$R" -e ERG_REDIS_CACHE_URL="$R" \
  -e SERVER_ADDRESS=0.0.0.0 -e SERVER_PORT=1606 -e SPRING_MODULITH_RUNTIME_VERIFICATION_ENABLED=false \
  -e ERG_SCHEDULING_ENABLED=false -e ERG_OUTBOX_WORKER_ENABLED=false -e NOTIFICATIONS__WORKER__ENABLED=false \
  -e ERG_REPORTING_WORKER_ENABLED=false -e NOTIFICATIONS__PUSH__ENABLED=false -e SPRING_MAIL_HOST=mail.invalid \
  "$IMAGE" >/dev/null
wait_healthy() {
  local s
  for _ in $(seq 1 90); do
    s=$(docker inspect -f '{{.State.Health.Status}} {{.State.Running}}' "$1")
    case "$s" in "healthy true") return 0;; *false|unhealthy*) break;; esac; sleep 2
  done
  echo "$1 did not become healthy ($s)"; docker logs "$1" 2>&1 | grep -E 'ERROR|FAILED|Caused by' | tail -20; return 1
}
wait_healthy $N-app
docker logs $N-app 2>&1 | grep -E 'Successfully applied|is up to date|Started ' | cut -c1-200
cleanup; trap - EXIT

log "switch production to $IMAGE"
PREVIOUS=$(docker inspect -f '{{.Config.Image}}' erg-backend-1 2>/dev/null || true)
sed -i "s|^ERG_APP_IMAGE=.*|ERG_APP_IMAGE=$IMAGE|" "$ENV_FILE"
[ -n "$PREVIOUS" ] && [ "$PREVIOUS" != "$IMAGE" ] && sed -i "s|^ERG_ROLLBACK_IMAGE=.*|ERG_ROLLBACK_IMAGE=$PREVIOUS|" "$ENV_FILE"
$C config --quiet
$C up -d --no-deps backend
wait_healthy erg-backend-1
# Single-file bind mounts keep the old inode after the unpack, so these must be recreated to see new config.
$C up -d --no-deps --force-recreate nginx alertmanager prometheus grafana node-exporter
wait_healthy erg-nginx-1
docker logs erg-backend-1 2>&1 | grep -E 'Successfully applied|is up to date' | cut -c1-200
log "deployed $SHA (previous image: ${PREVIOUS:-none}, backup: $DUMP)"

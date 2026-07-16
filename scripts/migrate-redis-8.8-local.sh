#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_DIR="$(cd "$ROOT/ops/docker" && pwd)"
ENV_FILE="$COMPOSE_DIR/.env.dev"
OLD_CONTAINER="erg-dev-redis-1"
OLD_VOLUME="erg-dev_redis-data"
POSTGRES_VOLUME="erg-dev_postgres-data"
EXECUTE="${1:-}"

[[ "$COMPOSE_DIR" == "$ROOT"/* ]] || { echo "Compose directory escaped workspace" >&2; exit 2; }
[[ -f "$ENV_FILE" ]] || { echo "Missing $ENV_FILE" >&2; exit 2; }

exact_container() {
  docker ps -a --filter "name=^/$1$" --format '{{.Names}}' | awk -v expected="$1" '$0 == expected'
}

exact_volume() {
  docker volume ls --filter "name=^$1$" --format '{{.Name}}' | awk -v expected="$1" '$0 == expected'
}

compose_dev() {
  docker compose --project-name erg-dev --project-directory "$COMPOSE_DIR" --env-file "$ENV_FILE" \
    -f "$COMPOSE_DIR/compose.yml" -f "$COMPOSE_DIR/compose.dev.yml" "$@"
}

wait_healthy() {
  local container="$1" status="" deadline=$((SECONDS + 120))
  while (( SECONDS < deadline )); do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    [[ "$status" == "healthy" ]] && return 0
    sleep 2
  done
  echo "$container did not become healthy (last status: $status)" >&2
  return 1
}

old_container="$(exact_container "$OLD_CONTAINER")"
old_volume="$(exact_volume "$OLD_VOLUME")"
postgres_before="$(exact_volume "$POSTGRES_VOLUME")"
printf 'Workspace: %s\nCompose project: erg-dev\nOld Redis container: %s\nOld Redis volume: %s\nPostgreSQL volume preserved: %s\n' \
  "$ROOT" "$old_container" "$old_volume" "$postgres_before"

if [[ "$EXECUTE" != "--execute" ]]; then
  echo "Dry run only. Re-run with --execute to remove only the exact old Redis container/volume."
  exit 0
fi

[[ -z "$old_container" ]] || docker rm --force "$OLD_CONTAINER"
[[ -z "$old_volume" ]] || docker volume rm "$OLD_VOLUME"
compose_dev pull redis-state redis-cache
compose_dev up -d --remove-orphans redis-state redis-cache
wait_healthy erg-dev-redis-state-1
wait_healthy erg-dev-redis-cache-1

docker exec erg-dev-redis-state-1 redis-cli --raw INFO server | grep -q 'redis_version:8.8.0'
docker exec erg-dev-redis-cache-1 redis-cli --raw INFO server | grep -q 'redis_version:8.8.0'
[[ -n "$(docker exec erg-dev-redis-state-1 redis-cli --raw COMMAND INFO INCREX)" ]]
docker exec erg-dev-redis-state-1 redis-cli --raw CONFIG GET maxmemory-policy | grep -q noeviction
docker exec erg-dev-redis-cache-1 redis-cli --raw CONFIG GET maxmemory-policy | grep -q allkeys-lfu
docker exec erg-dev-redis-state-1 redis-cli --raw CONFIG GET appendonly | grep -q yes
docker exec erg-dev-redis-cache-1 redis-cli --raw CONFIG GET appendonly | grep -q no

postgres_after="$(exact_volume "$POSTGRES_VOLUME")"
[[ "$postgres_before" == "$postgres_after" ]] || { echo "PostgreSQL volume changed" >&2; exit 1; }
echo "Redis 8.8 local migration passed: state and cache are healthy; PostgreSQL volume is unchanged."


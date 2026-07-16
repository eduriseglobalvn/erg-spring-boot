#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCKER_DIR="$ROOT/ops/docker"
BASE_COMPOSE="$DOCKER_DIR/compose.yml"
DEV_COMPOSE="$DOCKER_DIR/compose.dev.yml"
NATIVE_COMPOSE="$DOCKER_DIR/compose.native.yml"
ACTION="${1:-help}"
REPLICAS="${2:-2}"
IMAGE="${ERG_APP_IMAGE:-}"
DEV_ENV="${ERG_DEV_ENV_FILE:-$DOCKER_DIR/.env.dev}"
PRODUCTION_ENV="${ERG_PRODUCTION_ENV_FILE:-$DOCKER_DIR/.env.production}"

ensure_local_env() {
  local path="$1"
  local example="$2"
  if [[ ! -f "$path" ]]; then
    cp "$DOCKER_DIR/$example" "$path"
    printf 'Created local environment file: %s\n' "$path"
  fi
}

compose_dev() {
  docker compose --project-directory "$DOCKER_DIR" --env-file "$DEV_ENV" \
    -f "$BASE_COMPOSE" -f "$DEV_COMPOSE" "$@"
}

compose_native() {
  docker compose --project-directory "$DOCKER_DIR" --env-file "$PRODUCTION_ENV" \
    -f "$BASE_COMPOSE" -f "$NATIVE_COMPOSE" "$@"
}

case "$ACTION" in
  dev)
    ensure_local_env "$DEV_ENV" ".env.dev.example"
    compose_dev up -d --remove-orphans
    printf 'Local infrastructure: http://localhost:8080\n'
    ;;
  build)
    IMAGE="${IMAGE:-erg-spring:local-native}"
    docker buildx build --platform linux/amd64 --load --tag "$IMAGE" "$ROOT"
    ;;
  push)
    [[ -n "$IMAGE" ]] || { echo "Set ERG_APP_IMAGE to a versioned Registry tag" >&2; exit 2; }
    docker push "$IMAGE"
    ;;
  up)
    ensure_local_env "$PRODUCTION_ENV" ".env.production.example"
    export ERG_APP_IMAGE="${IMAGE:-erg-spring:local-native}"
    compose_native up -d --remove-orphans --scale "backend=$REPLICAS"
    ;;
  deploy)
    [[ -f "$PRODUCTION_ENV" ]] || { echo "Missing $PRODUCTION_ENV" >&2; exit 2; }
    compose_native pull
    compose_native up -d --no-build --remove-orphans --scale "backend=$REPLICAS"
    ;;
  down)
    [[ ! -f "$DEV_ENV" ]] || compose_dev down --remove-orphans
    [[ ! -f "$PRODUCTION_ENV" ]] || compose_native down --remove-orphans
    ;;
  logs)
    [[ -f "$PRODUCTION_ENV" ]] || { echo "Missing $PRODUCTION_ENV" >&2; exit 2; }
    compose_native logs -f nginx backend
    ;;
  config)
    ensure_local_env "$DEV_ENV" ".env.dev.example"
    ensure_local_env "$PRODUCTION_ENV" ".env.production.example"
    export ERG_APP_IMAGE="${IMAGE:-erg-spring:local-native}"
    compose_dev config --quiet
    compose_native config --quiet
    echo "Both Compose configurations are valid."
    ;;
  redis-migrate)
    "$ROOT/scripts/migrate-redis-8.8-local.sh" --execute
    ;;
  help|-h|--help)
    cat <<'USAGE'
Usage: ./scripts/docker.sh <action> [replicas]

  dev       Start PostgreSQL, Redis and Nginx for an IDE-run backend.
  build     Build a Linux amd64 native image locally.
  push      Push ERG_APP_IMAGE to the Registry.
  up        Run the native image locally with N backend replicas.
  deploy    Pull and run the prebuilt image. Never builds on the server.
  down      Stop development and native stacks.
  logs      Follow Nginx and backend logs.
  config    Validate both Compose configurations.
  redis-migrate  Destructively replace only local Redis 7.4 with Redis 8.8 state/cache.
USAGE
    ;;
  *)
    echo "Unknown action: $ACTION" >&2
    exit 2
    ;;
esac

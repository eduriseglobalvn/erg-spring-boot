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

assert_immutable_image() {
  local image="$1"
  [[ "$image" =~ ^[^/]+/.+:[0-9a-fA-F]{7,64}$ ]] || {
    echo "Image must use a registry/repository:git-sha tag (7-64 hex characters); floating tags such as latest, main or dev are not allowed" >&2
    exit 2
  }
}

assert_production_image() {
  local image="$1"
  [[ "$image" =~ ^[^/]+/.+@sha256:[0-9a-f]{64}$ ]] || {
    echo "Image must use a registry/repository@sha256:<64 lowercase hex digest>; Git-SHA tags and floating tags are not allowed for production deploy" >&2
    exit 2
  }
}

configured_value() {
  local name="$1"
  [[ -f "$PRODUCTION_ENV" ]] || return 0
  awk -F= -v key="$name" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$PRODUCTION_ENV"
}

deploy_production() {
  local target_image="${IMAGE:-$(configured_value ERG_APP_IMAGE)}"
  local rollback_image="${ERG_ROLLBACK_IMAGE:-$(configured_value ERG_ROLLBACK_IMAGE)}"
  assert_production_image "$target_image"
  assert_production_image "$rollback_image"
  [[ "$target_image" != "$rollback_image" ]] || {
    echo "Production image and rollback image must be different digests" >&2
    exit 2
  }

  export ERG_APP_IMAGE="$target_image"
  if compose_native pull && compose_native up -d --no-build --remove-orphans \
      --scale "backend=$REPLICAS" --wait --wait-timeout 180; then
    printf 'Production image is healthy: %s\n' "$target_image"
    return 0
  fi

  printf 'Production health gate failed for %s; rolling back to %s\n' "$target_image" "$rollback_image" >&2
  export ERG_APP_IMAGE="$rollback_image"
  if compose_native pull && compose_native up -d --no-build --remove-orphans \
      --scale "backend=$REPLICAS" --wait --wait-timeout 180; then
    printf 'Rollback completed: %s\n' "$rollback_image" >&2
    return 1
  fi
  echo "Production deploy failed and rollback also failed" >&2
  return 1
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
  release)
    [[ -n "$IMAGE" ]] || { echo "Set ERG_APP_IMAGE to a registry image with a Git SHA tag, for example ghcr.io/acme/erg-spring:abc1234" >&2; exit 2; }
    assert_immutable_image "$IMAGE"
    docker buildx build --platform linux/amd64 --push --tag "$IMAGE" "$ROOT"
    printf 'Published immutable native image %s\n' "$IMAGE"
    ;;
  push)
    [[ -n "$IMAGE" ]] || { echo "Set ERG_APP_IMAGE to a registry image with a Git SHA tag" >&2; exit 2; }
    assert_immutable_image "$IMAGE"
    docker push "$IMAGE"
    ;;
  test|up)
    ensure_local_env "$PRODUCTION_ENV" ".env.production.example"
    export ERG_APP_IMAGE="${IMAGE:-erg-spring:local-native}"
    compose_native up -d --remove-orphans --scale "backend=$REPLICAS"
    ;;
  prod|deploy)
    [[ -f "$PRODUCTION_ENV" ]] || { echo "Missing $PRODUCTION_ENV" >&2; exit 2; }
    deploy_production
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
  release   Build and push one immutable Linux amd64 image in one command.
  push      Push an existing immutable Git-SHA image to the Registry.
  test      Run the already-built native image locally with N backend replicas.
  up        Alias of test.
  prod      Pull and run a digest-pinned image with health gate and digest rollback.
  deploy    Alias of prod.
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

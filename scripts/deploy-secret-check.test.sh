#!/usr/bin/env bash
# Runs deploy.sh's check_secrets against fake env files: bash scripts/deploy-secret-check.test.sh
set -euo pipefail
DEPLOY="$(dirname "$0")/../ops/docker/deploy.sh"
# shellcheck source=/dev/null
source <(sed -n '/^envval()/p; /^SAMPLE_SECRET_SHA256=/,/"$/p; /^check_secrets()/,/^}/p' "$DEPLOY")
ENV_FILE=$(mktemp); trap 'rm -f "$ENV_FILE"' EXIT
fail=0
expect() { # $1 0|1 expected status, $2 label, rest: env lines
  local want=$1 label=$2 got=0; shift 2
  printf '%s\n' "$@" > "$ENV_FILE"
  check_secrets 2>/dev/null || got=1
  if [ "$got" = "$want" ]; then echo "ok   $label"; else echo "FAIL $label (got $got, want $want)"; fail=1; fi
}
good=(POSTGRES_PASSWORD=a1 POSTGRES_RUNTIME_PASSWORD=b2 REDIS_STATE_PASSWORD=c3 REDIS_CACHE_PASSWORD=d4)
expect 0 "four distinct real passwords pass" "${good[@]}"
expect 1 "missing variable is refused" POSTGRES_PASSWORD=a1 POSTGRES_RUNTIME_PASSWORD=b2 REDIS_STATE_PASSWORD=c3
expect 1 "blank variable is refused" "${good[@]}" REDIS_CACHE_PASSWORD=
expect 1 "old postgres sample is refused" "${good[@]}" POSTGRES_PASSWORD=replace-with-strong-postgres-password
expect 1 "old runtime sample is refused" "${good[@]}" POSTGRES_RUNTIME_PASSWORD=replace-with-different-runtime-password
expect 1 "old state sample is refused" "${good[@]}" REDIS_STATE_PASSWORD=replace-with-strong-state-redis-password
expect 1 "old cache sample is refused" "${good[@]}" REDIS_CACHE_PASSWORD=replace-with-strong-cache-redis-password
expect 0 "a sample value as substring of a real password passes" "${good[@]}" POSTGRES_PASSWORD=xreplace-with-strong-postgres-password
exit $fail

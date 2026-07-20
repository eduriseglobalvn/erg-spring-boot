#!/usr/bin/env sh
set -eu

: "${REDIS_STATE_URL:?REDIS_STATE_URL is required}"
: "${REDIS_CACHE_URL:?REDIS_CACHE_URL is required}"

expect_ok() {
  url="$1"; shift
  redis-cli -u "$url" --no-auth-warning "$@" >/dev/null
}

expect_denied() {
  url="$1"; shift
  output="$(redis-cli -u "$url" --no-auth-warning "$@" 2>&1 || true)"
  echo "$output" | grep -Eq 'NOPERM|ERR.*unknown command'
}

expect_ok "$REDIS_STATE_URL" PING
expect_ok "$REDIS_STATE_URL" SET erg:state:acl-check 1 PX 10000
expect_ok "$REDIS_STATE_URL" INCREX erg:state:acl-counter PX 10000 ENX
expect_ok "$REDIS_STATE_URL" EVAL 'return redis.call("GET", KEYS[1])' 1 erg:state:acl-check
expect_ok "$REDIS_STATE_URL" PUBLISH erg:state:acl-check ok

expect_ok "$REDIS_CACHE_URL" PING
expect_ok "$REDIS_CACHE_URL" SET erg:cache:acl-check ok PX 10000
expect_ok "$REDIS_CACHE_URL" GET erg:cache:acl-check

for command in 'CONFIG GET maxmemory' 'MODULE LIST' 'DEBUG HELP' 'SHUTDOWN NOSAVE' \
  'REPLICAOF NO ONE' 'FLUSHALL' 'FLUSHDB'; do
  # shellcheck disable=SC2086
  expect_denied "$REDIS_STATE_URL" $command
  # shellcheck disable=SC2086
  expect_denied "$REDIS_CACHE_URL" $command
done

echo "Redis ACL verification passed."

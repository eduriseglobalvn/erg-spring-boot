#!/usr/bin/env sh
set -eu
out="${1:-load/results/evidence}"
mkdir -p "$out"
git rev-parse HEAD > "$out/revision.txt"
git status --porcelain | sha256sum >> "$out/revision.txt"
docker compose --env-file ops/docker/.env.dev -f ops/docker/compose.yml -f ops/docker/compose.dev.yml images --format json > "$out/compose-images.json"
docker compose --env-file ops/docker/.env.dev -f ops/docker/compose.yml -f ops/docker/compose.dev.yml ps --format json > "$out/compose-ps.json"
for role in state cache; do
  id="$(docker compose --env-file ops/docker/.env.dev -f ops/docker/compose.yml -f ops/docker/compose.dev.yml ps -q "redis-${role}")"
  docker exec "$id" redis-cli --no-auth-warning INFO server memory persistence stats > "$out/redis-${role}-info.txt"
done
printf '{"environment":"local-docker","capturedAt":"%s"}\n' "$(date -u +%FT%TZ)" > "$out/manifest.json"
echo "Evidence captured without URLs, credentials, cookies, tokens, or ACL material."

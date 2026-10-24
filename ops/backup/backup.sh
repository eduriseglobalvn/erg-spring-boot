#!/usr/bin/env bash
# ops/backup/backup.sh
# Scheduled via cron on ERG Server (daily at 03:00)
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DOCKER_DIR="$ROOT/ops/docker"
BACKUP_DIR="$ROOT/ops/backup/data"
DATE=$(date +%Y%m%d_%H%M%S)
RETENTION_DAYS=7

ENV_FILE="$DOCKER_DIR/.env.production"
if [[ ! -f "$ENV_FILE" ]]; then
  ENV_FILE="$DOCKER_DIR/.env.dev"
fi

if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$ENV_FILE"
fi

POSTGRES_USER="${POSTGRES_USER:-erg}"
REDIS_STATE_PASSWORD="${REDIS_STATE_PASSWORD:-erg-local-redis-state}"

mkdir -p "$BACKUP_DIR"

echo "=== [$(date -Iseconds)] Starting ERG Database Backup ==="

# 1. PostgreSQL dump (all databases & roles)
echo "Dumping PostgreSQL databases..."
docker compose --project-directory "$DOCKER_DIR" \
  -f "$DOCKER_DIR/compose.yml" \
  exec -T postgres \
  pg_dumpall -U "$POSTGRES_USER" \
  | gzip > "$BACKUP_DIR/pg_full_$DATE.sql.gz"

echo "PostgreSQL dump created: $BACKUP_DIR/pg_full_$DATE.sql.gz ($(du -h "$BACKUP_DIR/pg_full_$DATE.sql.gz" | cut -f1))"

# 2. Redis State snapshot
echo "Triggering Redis State BGSAVE..."
docker compose --project-directory "$DOCKER_DIR" \
  -f "$DOCKER_DIR/compose.yml" \
  exec -T redis-state \
  redis-cli -a "$REDIS_STATE_PASSWORD" BGSAVE || true
sleep 3
docker compose --project-directory "$DOCKER_DIR" \
  -f "$DOCKER_DIR/compose.yml" \
  cp redis-state:/data/dump.rdb "$BACKUP_DIR/redis_state_$DATE.rdb" 2>/dev/null || true

# 3. Cleanup old backups
echo "Cleaning up backups older than $RETENTION_DAYS days..."
find "$BACKUP_DIR" -name "*.sql.gz" -mtime "+$RETENTION_DAYS" -delete
find "$BACKUP_DIR" -name "*.rdb" -mtime "+$RETENTION_DAYS" -delete

echo "=== [$(date -Iseconds)] Backup Completed Successfully ==="
ls -lh "$BACKUP_DIR"

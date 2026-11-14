#!/usr/bin/env bash
# ops/backup/backup.sh
# Scheduled via cron on ERG Server (daily at 03:00 Asia/Ho_Chi_Minh = 20:00 UTC)
# Local copies stay 7 days; an age-encrypted copy goes to a separate R2 bucket via rclone (restore-drill.md).
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DOCKER_DIR="$ROOT/ops/docker"
BACKUP_DIR="$ROOT/ops/backup/data"
NOW=${BACKUP_NOW:-$(date +%s)} # epoch seconds; BACKUP_NOW fakes the night for retention drills
DATE=$(date -d "@$NOW" +%Y%m%d_%H%M%S)
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

# Telegram notice; the token goes through curl's stdin config so it never shows in `ps`.
notify() {
  [[ -n "${TELEGRAM_BOT_TOKEN:-}" && -n "${TELEGRAM_CHAT_ID:-}" ]] || return 0
  curl -fsS -m 15 -o /dev/null -K - --data-urlencode "chat_id=$TELEGRAM_CHAT_ID"     --data-urlencode "text=$1" <<<"url = \"https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage\"" || true
}
trap 'notify "❌ Backup DB lỗi lúc $(date -Iseconds) (dòng $LINENO). Xem /backups trên bot hoặc backup.log."' ERR

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

# 2. Redis State snapshot (the container's REDISCLI_AUTH authenticates; -a would show the password in `ps`)
echo "Triggering Redis State BGSAVE..."
docker compose --project-directory "$DOCKER_DIR" \
  -f "$DOCKER_DIR/compose.yml" \
  exec -T redis-state \
  redis-cli BGSAVE || true
sleep 3
docker compose --project-directory "$DOCKER_DIR" \
  -f "$DOCKER_DIR/compose.yml" \
  cp redis-state:/data/dump.rdb "$BACKUP_DIR/redis_state_$DATE.rdb" 2>/dev/null || true

# 3. Cleanup old backups
echo "Cleaning up backups older than $RETENTION_DAYS days..."
find "$BACKUP_DIR" -name "*.sql.gz" -mtime "+$RETENTION_DAYS" -delete
find "$BACKUP_DIR" -name "*.rdb" -mtime "+$RETENTION_DAYS" -delete

# 4. Freshness metric for node-exporter's textfile collector; BackupStale alerts when it ages.
METRICS_DIR="$ROOT/ops/backup/metrics"
mkdir -p "$METRICS_DIR" && chmod 755 "$METRICS_DIR"
printf 'erg_backup_last_success_timestamp_seconds %s\nerg_backup_postgres_bytes %s\n' \
  "$(date +%s)" "$(stat -c %s "$BACKUP_DIR/pg_full_$DATE.sql.gz")" > "$METRICS_DIR/erg_backup.prom.tmp"
chmod 644 "$METRICS_DIR/erg_backup.prom.tmp" && mv "$METRICS_DIR/erg_backup.prom.tmp" "$METRICS_DIR/erg_backup.prom"

# 5. Off-host copy to BACKUP_RCLONE_REMOTE. With BACKUP_AGE_RECIPIENT set it is age-encrypted (secret key kept
# off this server); without it the plain .sql.gz is uploaded, readable by anyone with access to the remote.
# Remote keeps the newest 7 under daily/, 4 under weekly/ (Sunday runs) and 6 under monthly/ (runs on the 1st).
# Any failure here leaves the local backup and its metric intact; it is reported and OffsiteBackupStale alerts after 26h.
OFFSITE_TMP="$BACKUP_DIR/.offsite.age"
OFFSITE_EXT=.sql.gz
[[ -n "${BACKUP_AGE_RECIPIENT:-}" ]] && OFFSITE_EXT=.sql.gz.age
offsite_copy() {
  local name="pg_full_$DATE$OFFSITE_EXT" src="$BACKUP_DIR/pg_full_$DATE.sql.gz" tiers="daily:7" t dir
  [[ $(date -d "@$NOW" +%u) == 7 ]] && tiers+=" weekly:4"
  [[ $(date -d "@$NOW" +%d) == 01 ]] && tiers+=" monthly:6"
  if [[ -n "${BACKUP_AGE_RECIPIENT:-}" ]]; then
    age -r "$BACKUP_AGE_RECIPIENT" -o "$OFFSITE_TMP" "$src" || return 1
    src="$OFFSITE_TMP"
  fi
  for t in $tiers; do
    dir="$BACKUP_RCLONE_REMOTE/${t%:*}"
    rclone copyto "$src" "$dir/$name" || return 1
    # Prune by count, newest first by name (names sort by date): a missed night never drops an older copy early.
    rclone lsf --files-only --include "pg_full_*$OFFSITE_EXT" "$dir" | sort -r | tail -n "+$((${t#*:} + 1))"       | while read -r old; do rclone deletefile "$dir/$old" || exit 1; done || return 1
  done
}
if [[ -z "${BACKUP_RCLONE_REMOTE:-}" ]]; then
  OFFSITE="bị bỏ qua: thiếu BACKUP_RCLONE_REMOTE"
elif ! command -v rclone >/dev/null || { [[ -n "${BACKUP_AGE_RECIPIENT:-}" ]] && ! command -v age >/dev/null; }; then
  OFFSITE="bị bỏ qua: máy chưa cài rclone (hoặc age khi có BACKUP_AGE_RECIPIENT)"
elif offsite_copy; then
  OFFSITE=ok
  printf 'erg_backup_offsite_last_success_timestamp_seconds %s
' "$(date +%s)" > "$METRICS_DIR/erg_backup_offsite.prom.tmp"
  chmod 644 "$METRICS_DIR/erg_backup_offsite.prom.tmp" && mv "$METRICS_DIR/erg_backup_offsite.prom.tmp" "$METRICS_DIR/erg_backup_offsite.prom"
else
  OFFSITE="LỖI khi mã hoá/đẩy lên $BACKUP_RCLONE_REMOTE (xem backup.log)"
fi
rm -f "$OFFSITE_TMP"
if [[ $OFFSITE == ok ]]; then
  echo "Off-host copy uploaded: $BACKUP_RCLONE_REMOTE/daily/pg_full_$DATE$OFFSITE_EXT"
else
  echo "WARNING: off-host copy $OFFSITE" >&2
  notify "⚠️ Bản sao lưu ngoài máy $OFFSITE. Bản cục bộ pg_full_$DATE.sql.gz vẫn đã tạo."
fi

echo "=== [$(date -Iseconds)] Backup Completed Successfully ==="
notify "💾 Backup DB xong: pg_full_$DATE.sql.gz ($(du -h "$BACKUP_DIR/pg_full_$DATE.sql.gz" | cut -f1)), giữ $RETENTION_DAYS ngày; ngoài máy: $OFFSITE."
ls -lh "$BACKUP_DIR"

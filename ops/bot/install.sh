#!/usr/bin/env bash
# Installs or upgrades the ops bot on the server: bash install.sh <path-to-built-binary>
set -Eeuo pipefail
BIN="${1:?usage: install.sh <erg-ops-bot binary>}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIR=/opt/erg/bot
ENV_PROD=/opt/erg/ops/docker/.env.production

install -d -m 700 "$DIR"
install -m 755 "$BIN" "$DIR/erg-ops-bot.new" && mv "$DIR/erg-ops-bot.new" "$DIR/erg-ops-bot"

if [[ ! -f "$DIR/bot.env" ]]; then
  # First install: reuse the alert bot token and chat; the TOTP secret is new and only lives in bot.env.
  token="$(grep -E '^TELEGRAM_BOT_TOKEN=' "$ENV_PROD" | cut -d= -f2-)"
  chat="$(grep -E '^TELEGRAM_CHAT_ID=' "$ENV_PROD" | cut -d= -f2-)"
  totp="$(head -c 20 /dev/urandom | base32 | tr -d '=')"
  umask 077
  printf 'BOT_TELEGRAM_TOKEN=%s\nBOT_ALLOWED_USER_IDS=%s\nBOT_NOTIFY_CHAT_ID=%s\nBOT_TOTP_SECRET=%s\n' \
    "$token" "$chat" "$chat" "$totp" > "$DIR/bot.env"
  echo "Created $DIR/bot.env. Add the TOTP secret to an authenticator app: grep TOTP $DIR/bot.env"
fi

sudo install -m 644 "$HERE/erg-ops-bot.service" /etc/systemd/system/erg-ops-bot.service
sudo systemctl daemon-reload
sudo systemctl enable erg-ops-bot >/dev/null 2>&1
sudo systemctl restart erg-ops-bot
sleep 3
systemctl is-active erg-ops-bot

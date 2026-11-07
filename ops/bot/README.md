# ERG ops bot

Telegram bot (@erg_server_bot) that watches and operates the production server. Go, standard library only,
one static binary, runs on the server as systemd unit `erg-ops-bot` (user `erg`, group `docker`).

- Read commands: `/status /services /service /logs /errors /top /db /alerts /backups /disk /report /help`.
- Actions (need a TOTP code when `BOT_TOTP_SECRET` is set, else a confirm button): `/restart /backup /rollback /prune`.
- Watchdog: messages when a container is down ≥ 60 s, recovers or restarts; auto-restarts the backend after 3 min
  unhealthy, at most 3 times an hour. Daily report at `BOT_REPORT_HOUR` (Asia/Ho_Chi_Minh).
- Only allow-listed Telegram user IDs get answers. Log text is redacted (tokens, passwords, emails, phones) before sending.
- Shares the token with Alertmanager. Alertmanager only sends; the bot is the only `getUpdates` reader. Never run
  a second copy (another server, a laptop) with the same token: the two would steal each other's updates.

Config: `/opt/erg/bot/bot.env` (mode 600), see `config.go` for every `BOT_*` variable and its default.

Build and install (from a checkout, Docker needed, no local Go):

```bash
docker run --rm -v "$PWD/ops/bot:/src" -w /src -e CGO_ENABLED=0 -e GOOS=linux -e GOARCH=amd64 golang:1.24-alpine \
  sh -c 'go test ./... && go build -trimpath -ldflags="-s -w" -o erg-ops-bot .'
ssh erg@ssh.erg.edu.vn mkdir -p /tmp/botinst
scp ops/bot/erg-ops-bot ops/bot/install.sh ops/bot/erg-ops-bot.service erg@ssh.erg.edu.vn:/tmp/botinst/
ssh erg@ssh.erg.edu.vn bash /tmp/botinst/install.sh /tmp/botinst/erg-ops-bot
```

Run one read command by hand on the server: `set -a; . /opt/erg/bot/bot.env; set +a; /opt/erg/bot/erg-ops-bot run status`.

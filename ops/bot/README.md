# ERG ops bot

Telegram bot (@erg_server_bot) that watches and operates the production server. Go, standard library only,
one static binary, runs on the server as systemd unit `erg-ops-bot` (user `erg`, group `docker`).

- Read commands: `/status /services /service /logs /errors /top /db /alerts /backups /disk /report /testalert /help`.
- Actions (need a TOTP code when `BOT_TOTP_SECRET` is set, else a confirm button): `/restart /backup /rollback /prune`.
- Watchdog: messages when a container is down ≥ 60 s, recovers or restarts; auto-restarts the backend after 3 min
  unhealthy, at most 3 times an hour. Daily report at `BOT_REPORT_HOUR` (Asia/Ho_Chi_Minh).

What the bot tells the notify chat without being asked:

| Event | Message | How it is seen |
|---|---|---|
| Container unhealthy, exited, restarted by Docker, recovered | 🔴 / 🔁 / ✅ | `docker ps` + `docker inspect` every 30 s |
| Container still starting after 60 s | 🟡 | same |
| Container gone (removed, never created) | 🔴 "không còn trên máy" | every container seen before, plus `BOT_REQUIRED_SERVICES` |
| Deploy or rollback: a service runs another image, then is healthy | 🚀 "đổi bản A → B", ✅ "bản mới đã chạy ổn" | image name from `docker inspect` |
| Backend not ready although its container is healthy (Redis down, hung) | 🔴 "Backend không sẵn sàng" | `BOT_PROBE_LOCAL_URL`, 10 s timeout |
| Users cannot get in although the server is fine (tunnel, network) | 🌐 | `BOT_PROBE_PUBLIC_URL`, only checked while the local probe passes |
| Docker itself does not answer | 🔴 | `docker ps` failing for 60 s |
| Bot started | says whether the machine rebooted or only the bot restarted, and for how long nothing was watched | `BOT_ALIVE_FILE` stamp + `/proc/uptime` |

`deploy.sh` sends its own 🚀 start, ✅ done and ❌ failed (with the step and whether production was touched) to the same
chat. It runs the copy already on the server, so the first deploy that carries a new `deploy.sh` still uses the old one.

Notifications go through a queue: when Telegram cannot be reached they are kept (up to 200, in memory) and sent in
order once it answers, marked with how late they are.

**The whole server down** (power, network, disk) cannot be reported from the server. Set `BOT_HEARTBEAT_URL` to a
check URL of an outside dead-man's-switch service: the bot requests it every minute, and that service sends the
Telegram message when the requests stop. Until it is set, nothing reports a dead server.

`/testalert` pushes a fake alert through Alertmanager, to check that Alertmanager's own Telegram route works.
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

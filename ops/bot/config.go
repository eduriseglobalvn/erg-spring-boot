package main

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
	_ "time/tzdata" // the static binary has no system zoneinfo
)

type Config struct {
	Token        string
	AllowedIDs   map[int64]bool
	NotifyChatID int64
	TOTPSecret   string // base32; empty means actions only need a confirm button
	PromURL      string
	AMURL        string
	Project      string // docker compose project, containers are <project>-<service>-1
	DockerDir    string
	EnvFile      string
	BackupScript string
	BackupLog    string
	BackupDirs   []string
	AuditLog     string
	Location     *time.Location
	ReportHour   int
}

func env(key, def string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return def
}

func LoadConfig() (Config, error) {
	c := Config{
		Token:        env("BOT_TELEGRAM_TOKEN", ""),
		AllowedIDs:   map[int64]bool{},
		TOTPSecret:   strings.ToUpper(strings.ReplaceAll(env("BOT_TOTP_SECRET", ""), " ", "")),
		PromURL:      env("BOT_PROMETHEUS_URL", "http://127.0.0.1:9090"),
		AMURL:        env("BOT_ALERTMANAGER_URL", "http://127.0.0.1:9093"),
		Project:      env("BOT_COMPOSE_PROJECT", "erg"),
		DockerDir:    env("BOT_DOCKER_DIR", "/opt/erg/ops/docker"),
		BackupScript: env("BOT_BACKUP_SCRIPT", "/opt/erg/ops/backup/backup.sh"),
		BackupLog:    env("BOT_BACKUP_LOG", "/opt/erg/ops/backup/backup.log"),
		BackupDirs:   strings.Split(env("BOT_BACKUP_DIRS", "/opt/erg/ops/backup/data,/opt/erg/backups"), ","),
		AuditLog:     env("BOT_AUDIT_LOG", "/opt/erg/bot/audit.log"),
	}
	c.EnvFile = env("BOT_ENV_FILE", c.DockerDir+"/.env.production")
	if c.Token == "" {
		return c, fmt.Errorf("BOT_TELEGRAM_TOKEN is required")
	}
	for _, raw := range strings.Split(env("BOT_ALLOWED_USER_IDS", ""), ",") {
		if raw = strings.TrimSpace(raw); raw == "" {
			continue
		}
		id, err := strconv.ParseInt(raw, 10, 64)
		if err != nil {
			return c, fmt.Errorf("BOT_ALLOWED_USER_IDS: %q is not a number", raw)
		}
		c.AllowedIDs[id] = true
	}
	if len(c.AllowedIDs) == 0 {
		return c, fmt.Errorf("BOT_ALLOWED_USER_IDS is required: the bot controls the server")
	}
	notify, err := strconv.ParseInt(env("BOT_NOTIFY_CHAT_ID", "0"), 10, 64)
	if err != nil {
		return c, fmt.Errorf("BOT_NOTIFY_CHAT_ID: %v", err)
	}
	c.NotifyChatID = notify
	if c.TOTPSecret != "" {
		if _, err := decodeBase32(c.TOTPSecret); err != nil {
			return c, fmt.Errorf("BOT_TOTP_SECRET is not base32: %v", err)
		}
	}
	if c.Location, err = time.LoadLocation(env("BOT_TIMEZONE", "Asia/Ho_Chi_Minh")); err != nil {
		return c, err
	}
	if c.ReportHour, err = strconv.Atoi(env("BOT_REPORT_HOUR", "8")); err != nil || c.ReportHour < -1 || c.ReportHour > 23 {
		return c, fmt.Errorf("BOT_REPORT_HOUR must be 0-23, or -1 to disable")
	}
	return c, nil
}

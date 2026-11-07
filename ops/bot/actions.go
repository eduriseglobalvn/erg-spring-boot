package main

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"
)

func (b *Bot) actRestart(ctx context.Context, args []string) (*Action, error) {
	if len(args) == 0 {
		return nil, fmt.Errorf("cần tên service, ví dụ /restart backend")
	}
	c, err := b.docker.Resolve(ctx, args[0])
	if err != nil {
		return nil, err
	}
	return &Action{Label: "Restart " + c.Service, Run: func(ctx context.Context) (string, error) {
		if err := b.docker.Restart(ctx, c.Name); err != nil {
			return "", err
		}
		in, err := b.docker.WaitHealthy(ctx, c.Name, 3*time.Minute)
		if err != nil {
			logs, _ := b.docker.Logs(ctx, c.Name, 30, "")
			return pre(Redact(stripTimestamps(logs))), err
		}
		return fmt.Sprintf("%s đang %s %s", esc(c.Service), esc(in.Status), esc(in.Health)), nil
	}}, nil
}

func (b *Bot) actBackup(_ context.Context, _ []string) (*Action, error) {
	return &Action{Label: "Backup database ngay", Run: func(ctx context.Context) (string, error) {
		out, err := run(ctx, 9*time.Minute, "bash", b.cfg.BackupScript)
		tail := lastLines(Redact(out), 12)
		if err != nil {
			return pre(tail), fmt.Errorf("backup.sh: %v", err)
		}
		r, _ := b.cmdBackups(ctx, nil)
		return pre(tail) + "\n\n" + r.HTML, nil
	}}, nil
}

var imageVar = regexp.MustCompile(`(?m)^(ERG_APP_IMAGE|ERG_ROLLBACK_IMAGE)=(.*)$`)

// readImages returns the current and rollback image names from the env file.
func readImages(env string) (current, rollback string) {
	for _, m := range imageVar.FindAllStringSubmatch(env, -1) {
		v := strings.Trim(strings.TrimSpace(m[2]), `"'`)
		if m[1] == "ERG_APP_IMAGE" {
			current = v
		} else {
			rollback = v
		}
	}
	return current, rollback
}

// swapImages exchanges the two image values and leaves every other line untouched.
func swapImages(env string) string {
	current, rollback := readImages(env)
	return imageVar.ReplaceAllStringFunc(env, func(line string) string {
		if strings.HasPrefix(line, "ERG_APP_IMAGE=") {
			return "ERG_APP_IMAGE=" + rollback
		}
		return "ERG_ROLLBACK_IMAGE=" + current
	})
}

func (b *Bot) actRollback(ctx context.Context, _ []string) (*Action, error) {
	raw, err := os.ReadFile(b.cfg.EnvFile)
	if err != nil {
		return nil, fmt.Errorf("không đọc được file env")
	}
	current, rollback := readImages(string(raw))
	if current == "" || rollback == "" || current == rollback {
		return nil, fmt.Errorf("không có image rollback khác image hiện tại")
	}
	if _, err := run(ctx, 15*time.Second, "docker", "image", "inspect", rollback); err != nil {
		return nil, fmt.Errorf("image %s không còn trên server", rollback)
	}
	label := fmt.Sprintf("Rollback backend %s → %s (DB migration KHÔNG bị lùi; chỉ an toàn khi bản cũ chạy được với schema mới)", current, rollback)
	return &Action{Label: label, Run: func(ctx context.Context) (string, error) {
		now, err := os.ReadFile(b.cfg.EnvFile)
		if err != nil {
			return "", err
		}
		info, _ := os.Stat(b.cfg.EnvFile)
		tmp := b.cfg.EnvFile + ".bot-tmp"
		if err := os.WriteFile(tmp, []byte(swapImages(string(now))), info.Mode().Perm()); err != nil {
			return "", err
		}
		if err := os.Rename(tmp, b.cfg.EnvFile); err != nil {
			return "", err
		}
		out, err := b.compose(ctx, "up", "-d", "--no-deps", "backend")
		if err != nil {
			return pre(lastLines(Redact(out), 15)), err
		}
		if _, err := b.docker.WaitHealthy(ctx, b.cfg.Project+"-backend-1", 4*time.Minute); err != nil {
			return "Backend chưa healthy. Xem /logs backend; muốn quay lại thì /rollback lần nữa.", err
		}
		return "Backend đang chạy " + esc(rollback) + ". Gõ /rollback lần nữa để quay về " + esc(current) + ".", nil
	}}, nil
}

func (b *Bot) compose(ctx context.Context, args ...string) (string, error) {
	base := []string{"compose", "--project-directory", b.cfg.DockerDir, "--env-file", b.cfg.EnvFile, "-p", b.cfg.Project}
	for _, f := range []string{"compose.yml", "compose.native.yml", "compose.monitor.yml"} {
		base = append(base, "-f", filepath.Join(b.cfg.DockerDir, f))
	}
	return run(ctx, 8*time.Minute, "docker", append(base, args...)...)
}

func (b *Bot) actPrune(_ context.Context, _ []string) (*Action, error) {
	return &Action{Label: "Dọn image không dùng và build cache cũ hơn 24h", Run: func(ctx context.Context) (string, error) {
		// shortcut: prunes only dangling images, so rollback images stay; a full `-a` prune needs a keep-list.
		img, err := run(ctx, 5*time.Minute, "docker", "image", "prune", "-f")
		if err != nil {
			return pre(img), err
		}
		bld, err := run(ctx, 5*time.Minute, "docker", "builder", "prune", "-f", "--filter", "until=24h")
		return pre(lastLines(img, 3) + "\n" + lastLines(bld, 3)), err
	}}, nil
}

func lastLines(s string, n int) string {
	lines := strings.Split(strings.TrimRight(s, "\n"), "\n")
	if len(lines) > n {
		lines = lines[len(lines)-n:]
	}
	return strings.Join(lines, "\n")
}

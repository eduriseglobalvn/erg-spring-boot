package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"time"
)

// spell tracks one thing that can go bad between polls.
type spell struct {
	badSince time.Time
	notified bool
}

// step records this poll. down is true once, when the thing has been bad for notifyAfter;
// recovered is how long the spell lasted, set once when it ends after a "down" was reported.
func (s *spell) step(bad bool, now time.Time) (down bool, recovered time.Duration) {
	if !bad {
		if s.notified {
			recovered = now.Sub(s.badSince)
		}
		*s = spell{}
		return false, recovered
	}
	if s.badSince.IsZero() {
		s.badSince = now
	}
	if !s.notified && now.Sub(s.badSince) >= notifyAfter {
		s.notified = true
		return true, 0
	}
	return false, 0
}

// checkReady GETs the backend's /ready and returns nil only for HTTP 200 with status "ready", which the backend
// sends when Postgres and Redis both answer. A reply slower than the client timeout is a failure.
func checkReady(ctx context.Context, client *http.Client, target string) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
	if err != nil {
		return err
	}
	resp, err := client.Do(req)
	if err != nil {
		var ue *url.Error
		if errors.As(err, &ue) {
			if ue.Timeout() {
				return fmt.Errorf("không trả lời trong %s", client.Timeout)
			}
			return ue.Err
		}
		return err
	}
	defer resp.Body.Close()
	var body struct {
		Data struct {
			Status string          `json:"status"`
			Checks map[string]bool `json:"checks"`
		} `json:"data"`
	}
	decodeErr := json.NewDecoder(resp.Body).Decode(&body)
	var failed []string
	for name, ok := range body.Data.Checks {
		if !ok {
			failed = append(failed, name)
		}
	}
	sort.Strings(failed)
	switch {
	case len(failed) > 0:
		return fmt.Errorf("HTTP %d, hỏng: %s", resp.StatusCode, strings.Join(failed, ", "))
	case resp.StatusCode != http.StatusOK:
		return fmt.Errorf("HTTP %d", resp.StatusCode)
	case decodeErr != nil || body.Data.Status != "ready":
		return fmt.Errorf("HTTP 200 nhưng không phải trả lời của /ready")
	}
	return nil
}

// probes is what the watchdog remembers about the two readiness checks.
type probes struct{ local, public spell }

// messages turns this poll's results into notifications. The public check is skipped while the local one fails:
// a backend that is down inside the server is also down from outside, and one message about it is enough.
func (p *probes) messages(localErr, publicErr error, publicURL string, now time.Time) []string {
	var out []string
	down, recovered := p.local.step(localErr != nil, now)
	if down {
		out = append(out, fmt.Sprintf("🔴 <b>Backend không sẵn sàng</b> dù container có thể vẫn báo healthy: %s (đã %s). Xem /status và /errors backend 10m.",
			esc(localErr.Error()), durH(now.Sub(p.local.badSince))))
	}
	if recovered > 0 {
		out = append(out, fmt.Sprintf("✅ <b>Backend</b> sẵn sàng lại sau %s.", durH(recovered)))
	}
	if publicURL == "" || localErr != nil {
		return out
	}
	down, recovered = p.public.step(publicErr != nil, now)
	if down {
		out = append(out, fmt.Sprintf("🌐 <b>Người dùng không vào được hệ thống</b>: %s lỗi (%s, đã %s) trong khi backend trong máy vẫn khoẻ. Nghi Cloudflare Tunnel hoặc đường mạng: kiểm dịch vụ cloudflared trên máy chủ.",
			esc(publicURL), esc(publicErr.Error()), durH(now.Sub(p.public.badSince))))
	}
	if recovered > 0 {
		out = append(out, fmt.Sprintf("✅ <b>Tên miền công khai</b> vào lại được sau %s.", durH(recovered)))
	}
	return out
}

// probe runs both checks. The public URL leaves the server and comes back through the tunnel, the way users arrive.
func (b *Bot) probe(ctx context.Context, p *probes, now time.Time) []string {
	if b.cfg.ProbeLocalURL == "" {
		return nil
	}
	client := &http.Client{Timeout: 10 * time.Second}
	localErr := checkReady(ctx, client, b.cfg.ProbeLocalURL)
	var publicErr error
	if localErr == nil && b.cfg.ProbePublicURL != "" {
		publicErr = checkReady(ctx, client, b.cfg.ProbePublicURL)
	}
	return p.messages(localErr, publicErr, b.cfg.ProbePublicURL, now)
}

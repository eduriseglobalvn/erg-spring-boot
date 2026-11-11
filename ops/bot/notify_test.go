package main

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestOutboxKeepsOrderAcrossTransientFailure(t *testing.T) {
	o := newOutbox()
	t0 := time.Date(2026, 11, 11, 3, 0, 0, 0, time.UTC)
	o.add("one", t0)
	o.add("two", t0)
	var sent []string
	down := func(string) error { return fmt.Errorf("telegram sendMessage: request failed: %w", errTransient) }
	up := func(html string) error { sent = append(sent, html); return nil }
	if o.flush(t0, time.UTC, down) {
		t.Fatal("queue reported empty while Telegram was down")
	}
	if !o.flush(t0.Add(5*time.Minute), time.UTC, up) {
		t.Fatal("queue not empty after Telegram came back")
	}
	if len(sent) != 2 || !strings.HasSuffix(sent[0], "one") || !strings.HasSuffix(sent[1], "two") {
		t.Fatalf("lost or reordered: %q", sent)
	}
	if !strings.Contains(sent[0], "Gửi trễ 5 phút, sự việc lúc 03:00 11/11") {
		t.Fatalf("late message does not say when it happened: %q", sent[0])
	}
}

func TestOutboxDropsWhatTelegramRefusesAndCountsOverflow(t *testing.T) {
	o := newOutbox()
	now := time.Now()
	for i := 0; i < outboxMax+3; i++ {
		o.add(fmt.Sprint("m", i), now)
	}
	var sent []string
	calls := 0
	ok := o.flush(now, time.UTC, func(html string) error {
		if calls++; calls == 1 {
			return errors.New("telegram sendMessage: Bad Request: cannot parse entities") // permanent: must not block the queue
		}
		sent = append(sent, html)
		return nil
	})
	if !ok || len(sent) != outboxMax-1 {
		t.Fatalf("ok=%t sent=%d, want the queue drained past the refused message", ok, len(sent))
	}
	if !strings.Contains(sent[0], "3 tin cũ hơn đã bị bỏ") || strings.Contains(sent[1], "đã bị bỏ") {
		t.Fatalf("overflow notice wrong: %q / %q", sent[0], sent[1])
	}
}

func TestTelegramMarksOnlyServerSideFailuresTransient(t *testing.T) {
	code := 502
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(code)
		_, _ = w.Write([]byte(`{"ok":false,"description":"Bad Request: chat not found"}`))
	}))
	defer srv.Close()
	tg := &TG{base: srv.URL, http: srv.Client()}
	if err := tg.Send(context.Background(), 1, "x", nil); !errors.Is(err, errTransient) {
		t.Fatalf("502 should be retried, got %v", err)
	}
	code = 400
	if err := tg.Send(context.Background(), 1, "x", nil); err == nil || errors.Is(err, errTransient) {
		t.Fatalf("400 should fail for good, got %v", err)
	}
	srv.Close()
	if err := tg.Send(context.Background(), 1, "x", nil); !errors.Is(err, errTransient) {
		t.Fatalf("no connection should be retried, got %v", err)
	}
}

func TestBootMessageTellsRebootFromBotRestart(t *testing.T) {
	loc := time.FixedZone("ICT", 7*3600)
	now := time.Date(2026, 11, 11, 7, 20, 0, 0, time.UTC)
	last := now.Add(-18 * time.Minute)
	reboot := bootMessage(now, last, 90*time.Second, loc)
	if !strings.Contains(reboot, "Máy chủ vừa khởi động lại") || !strings.Contains(reboot, "từ 14:02 11/11 đến 14:20 11/11 (khoảng 18 phút)") {
		t.Fatalf("reboot message: %q", reboot)
	}
	restart := bootMessage(now, now.Add(-20*time.Second), 72*time.Hour, loc)
	if !strings.Contains(restart, "máy chủ không tắt") || strings.Contains(restart, "Máy chủ vừa") {
		t.Fatalf("bot restart message: %q", restart)
	}
	if first := bootMessage(now, time.Time{}, time.Hour, loc); !strings.Contains(first, "Bot vận hành đã khởi động") {
		t.Fatalf("first start message: %q", first)
	}
}

func TestCheckReady(t *testing.T) {
	status, body, delay := 200, `{"data":{"status":"ready","checks":{"app":true,"db.postgres":true,"redis":true}}}`, time.Duration(0)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		time.Sleep(delay)
		w.WriteHeader(status)
		_, _ = w.Write([]byte(body))
	}))
	defer srv.Close()
	client := &http.Client{Timeout: 300 * time.Millisecond}
	check := func() error { return checkReady(context.Background(), client, srv.URL) }
	if err := check(); err != nil {
		t.Fatalf("ready backend: %v", err)
	}
	// Redis down leaves the backend answering 200, so the container stays "healthy"; the probe must still fail.
	body = `{"data":{"status":"degraded","checks":{"app":true,"db.postgres":true,"redis":false}}}`
	if err := check(); err == nil || !strings.Contains(err.Error(), "redis") {
		t.Fatalf("degraded: %v", err)
	}
	status, body = 503, `{"data":{"status":"degraded","checks":{"app":true,"db.postgres":false,"redis":true}}}`
	if err := check(); err == nil || !strings.Contains(err.Error(), "db.postgres") {
		t.Fatalf("db down: %v", err)
	}
	status, body = 200, "<html>Cloudflare</html>"
	if err := check(); err == nil {
		t.Fatal("an HTML page passed as ready")
	}
	status, body = 502, "Bad Gateway"
	if err := check(); err == nil || !strings.Contains(err.Error(), "502") {
		t.Fatalf("502: %v", err)
	}
	status, body, delay = 200, `{"data":{"status":"ready"}}`, time.Second
	if err := check(); err == nil || !strings.Contains(err.Error(), "không trả lời") {
		t.Fatalf("hung backend: %v", err)
	}
}

func TestProbeMessagesSeparateBackendFromTunnel(t *testing.T) {
	const pub = "https://api.example/ready"
	bad := errors.New("HTTP 502")
	t0 := time.Now()
	at := func(s int) time.Time { return t0.Add(time.Duration(s) * time.Second) }

	var p probes
	if m := p.messages(nil, bad, pub, at(0)); len(m) != 0 {
		t.Fatalf("one failed poll must not alert: %v", m)
	}
	if m := p.messages(nil, bad, pub, at(60)); len(m) != 1 || !strings.Contains(m[0], "Người dùng không vào được") {
		t.Fatalf("tunnel down: %v", m)
	}
	if m := p.messages(nil, bad, pub, at(90)); len(m) != 0 {
		t.Fatalf("repeated: %v", m)
	}
	if m := p.messages(nil, nil, pub, at(120)); len(m) != 1 || !strings.Contains(m[0], "Tên miền công khai") {
		t.Fatalf("tunnel recovery: %v", m)
	}

	p = probes{}
	p.messages(bad, bad, pub, at(0))
	m := p.messages(bad, bad, pub, at(60))
	if len(m) != 1 || !strings.Contains(m[0], "Backend không sẵn sàng") {
		t.Fatalf("backend down must give one message, not a tunnel one too: %v", m)
	}
	if m := p.messages(nil, nil, pub, at(90)); len(m) != 1 || !strings.Contains(m[0], "sẵn sàng lại") {
		t.Fatalf("backend recovery: %v", m)
	}
}

func TestWatchdogReportsNewImageAndMissingContainer(t *testing.T) {
	st := &svcState{restarts: -1}
	if m := st.changes("backend", Inspect{Image: "erg-spring:aaa-jvm"}, false); len(m) != 0 {
		t.Fatalf("first sight must be silent: %v", m)
	}
	m := st.changes("backend", Inspect{Image: "erg-spring:bbb-jvm"}, false)
	if len(m) != 1 || !strings.Contains(m[0], "erg-spring:aaa-jvm → erg-spring:bbb-jvm") || !st.newImage {
		t.Fatalf("deploy not reported: %v", m)
	}

	b := &Bot{audit: &Audit{path: t.TempDir() + "/a.log"}}
	states := map[string]*svcState{"erg-backend-1": st, "erg-postgres-1": {restarts: 0}, "erg-backend-run-ab12": {restarts: 0}}
	t0 := time.Now()
	list := []Container{{Name: "erg-backend-1", Service: "backend", State: "running", Status: "Up 5s (health: starting)"}}
	if m := b.watchOnce(context.Background(), list, states, t0, false); len(m) != 0 {
		t.Fatalf("too early: %v", m)
	}
	m = b.watchOnce(context.Background(), list, states, t0.Add(61*time.Second), false)
	if len(m) != 2 || !strings.Contains(m[0], "đang khởi động") || !strings.Contains(m[1], "erg-postgres-1</b> không còn trên máy") {
		t.Fatalf("want a starting notice and one missing container, got %v", m)
	}
	list = []Container{
		{Name: "erg-backend-1", Service: "backend", State: "running", Status: "Up 2 minutes (healthy)"},
		{Name: "erg-postgres-1", Service: "postgres", State: "running", Status: "Up 3s (healthy)"},
	}
	m = b.watchOnce(context.Background(), list, states, t0.Add(120*time.Second), false)
	if len(m) != 2 || !strings.Contains(m[0], "bản mới đã chạy ổn") || !strings.Contains(m[1], "postgres</b> đã ổn lại") {
		t.Fatalf("recovery: %v", m)
	}
}

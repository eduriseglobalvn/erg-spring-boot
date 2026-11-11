package main

import (
	"context"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"
)

// outbox holds notifications until Telegram takes them, so a network blip does not lose an alert.
// shortcut: in memory only, so messages still waiting when the bot stops are lost; write them to disk if that ever bites.
type outbox struct {
	mu   sync.Mutex
	q    []queued
	lost int
	wake chan struct{}
}

type queued struct {
	html string
	at   time.Time
}

const outboxMax = 200

func newOutbox() *outbox { return &outbox{wake: make(chan struct{}, 1)} }

func (o *outbox) add(html string, now time.Time) {
	o.mu.Lock()
	if len(o.q) >= outboxMax {
		o.q = o.q[1:]
		o.lost++
	}
	o.q = append(o.q, queued{html, now})
	o.mu.Unlock()
	select {
	case o.wake <- struct{}{}:
	default:
	}
}

// flush sends the queue oldest first and stops at the first transient failure, keeping that message for the next try.
// It reports whether the queue is now empty. A message Telegram refuses for good (bad HTML) is logged and dropped.
func (o *outbox) flush(now time.Time, loc *time.Location, send func(html string) error) bool {
	for {
		o.mu.Lock()
		if len(o.q) == 0 {
			o.mu.Unlock()
			return true
		}
		m, lost := o.q[0], o.lost
		o.q = o.q[1:]
		o.mu.Unlock()

		text := m.html
		if late := now.Sub(m.at); late >= time.Minute {
			text = fmt.Sprintf("⏱ <i>Gửi trễ %s, sự việc lúc %s</i>\n%s", durH(late), m.at.In(loc).Format("15:04 02/01"), text)
		}
		if lost > 0 {
			text = fmt.Sprintf("⚠️ <i>%d tin cũ hơn đã bị bỏ vì hàng chờ đầy.</i>\n%s", lost, text)
		}
		err := send(text)
		o.mu.Lock()
		switch {
		case errors.Is(err, errTransient):
			o.q = append([]queued{m}, o.q...)
			o.mu.Unlock()
			return false
		case err != nil:
			log.Printf("notify dropped: %v", err)
		default:
			o.lost -= lost
		}
		o.mu.Unlock()
	}
}

func (b *Bot) notify(_ context.Context, html string) {
	if b.cfg.NotifyChatID == 0 || html == "" {
		return
	}
	b.out.add(html, time.Now())
}

// sendLoop is the only sender of notifications, so they arrive in order.
// It backs off from 5 s to about 1 min while Telegram is unreachable.
func (b *Bot) sendLoop(ctx context.Context) {
	wait := 5 * time.Second
	for ctx.Err() == nil {
		empty := b.out.flush(time.Now(), b.cfg.Location, func(html string) error {
			return b.tg.Send(ctx, b.cfg.NotifyChatID, html, nil)
		})
		if empty {
			wait = 5 * time.Second
			select {
			case <-ctx.Done():
			case <-b.out.wake:
			}
			continue
		}
		log.Printf("notify: Telegram chưa nhận, thử lại sau %s", wait)
		sleep(ctx, wait)
		if wait < time.Minute {
			wait *= 2
		}
	}
}

// alive stamps the state file and pings the outside dead-man's switch once a minute. When the whole server is gone
// nothing on it can say so; the outside service notices the pings stopped and sends the message instead.
func (b *Bot) alive(ctx context.Context) {
	client := &http.Client{Timeout: 10 * time.Second}
	failing := false
	for ctx.Err() == nil {
		if err := os.WriteFile(b.cfg.AliveFile, []byte(time.Now().UTC().Format(time.RFC3339)), 0o600); err != nil {
			log.Printf("alive file: %v", err)
		}
		if b.cfg.HeartbeatURL != "" {
			ok := ping(ctx, client, b.cfg.HeartbeatURL)
			if ok == failing { // log only when it changes; the URL is a secret and is never logged
				log.Printf("heartbeat ok=%t", ok)
			}
			failing = !ok
		}
		sleep(ctx, time.Minute)
	}
}

func ping(ctx context.Context, client *http.Client, target string) bool {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
	if err != nil {
		return false
	}
	resp, err := client.Do(req)
	if err != nil {
		return false
	}
	resp.Body.Close()
	return resp.StatusCode < 300
}

// lastAlive reads the stamp the previous run left; zero when there is none.
func lastAlive(path string) time.Time {
	raw, err := os.ReadFile(path)
	if err != nil {
		return time.Time{}
	}
	t, _ := time.Parse(time.RFC3339, strings.TrimSpace(string(raw)))
	return t
}

// hostUptime is how long the machine has been up; zero when /proc/uptime cannot be read.
func hostUptime() time.Duration {
	raw, err := os.ReadFile("/proc/uptime")
	if err != nil {
		return 0
	}
	secs, _ := strconv.ParseFloat(strings.Fields(string(raw) + " 0")[0], 64)
	return time.Duration(secs * float64(time.Second))
}

// bootMessage says what happened while the bot was silent: the machine rebooted, or only the bot restarted.
func bootMessage(now, last time.Time, uptime time.Duration, loc *time.Location) string {
	const help = " Gõ /help để xem lệnh."
	if last.IsZero() {
		return "🤖 <b>Bot vận hành đã khởi động.</b>" + help
	}
	clock := func(t time.Time) string { return t.In(loc).Format("15:04 02/01") }
	gap := fmt.Sprintf("Bot không theo dõi từ %s đến %s (khoảng %s).", clock(last), clock(now), durH(now.Sub(last)))
	if booted := now.Add(-uptime); uptime > 0 && booted.After(last) {
		return fmt.Sprintf("🖥 <b>Máy chủ vừa khởi động lại</b> lúc %s. %s Trong lúc đó không có cảnh báo nào được gửi. Xem /status.", clock(booted), gap)
	}
	if uptime > 0 {
		return fmt.Sprintf("🤖 <b>Bot khởi động lại</b>, máy chủ không tắt (đã chạy %s). %s", durH(uptime), gap) + help
	}
	return "🤖 <b>Bot khởi động lại.</b> " + gap + help
}

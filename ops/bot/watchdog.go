package main

import (
	"context"
	"fmt"
	"log"
	"time"
)

// svcState is what the watchdog remembers about one container between polls.
type svcState struct {
	badSince  time.Time // zero while healthy
	notified  bool      // a "down" message was sent for the current bad spell
	restarts  int
	autoFixes []time.Time
}

const (
	watchEvery     = 30 * time.Second
	notifyAfter    = 60 * time.Second
	autoRestartAt  = 3 * time.Minute
	maxAutoPerHour = 3
)

func (b *Bot) watchdog(ctx context.Context) {
	states := map[string]*svcState{}
	first := true
	for ctx.Err() == nil {
		if list, err := b.docker.Containers(ctx); err != nil {
			log.Printf("watchdog: %v", err)
		} else {
			for _, msg := range b.watchOnce(ctx, list, states, time.Now(), first) {
				b.notify(ctx, msg)
			}
			first = false
		}
		sleep(ctx, watchEvery)
	}
}

// watchOnce compares the containers with the last poll and returns the messages to send.
func (b *Bot) watchOnce(ctx context.Context, list []Container, states map[string]*svcState, now time.Time, first bool) []string {
	var out []string
	for _, c := range list {
		st := states[c.Name]
		if st == nil {
			st = &svcState{restarts: -1}
			states[c.Name] = st
		}
		if in, err := b.docker.Inspect(ctx, c.Name); err == nil {
			if st.restarts >= 0 && in.Restarts > st.restarts && !first {
				why := ""
				if in.OOMKilled {
					why = " (hết RAM)"
				}
				out = append(out, fmt.Sprintf("🔁 <b>%s</b> tự khởi động lại%s, lần thứ %d.", esc(c.Service), why, in.Restarts))
			}
			st.restarts = in.Restarts
		}
		if c.Healthy() {
			if st.notified {
				out = append(out, fmt.Sprintf("✅ <b>%s</b> đã ổn lại sau %s.", esc(c.Service), durH(now.Sub(st.badSince))))
			}
			st.badSince, st.notified = time.Time{}, false
			continue
		}
		if st.badSince.IsZero() {
			st.badSince = now
		}
		bad := now.Sub(st.badSince)
		if !st.notified && bad >= notifyAfter {
			st.notified = true
			out = append(out, fmt.Sprintf("🔴 <b>%s</b> lỗi: %s (%s). Xem /service %s", esc(c.Service), esc(c.Status), durH(bad), esc(c.Service)))
		}
		if c.Service == "backend" && bad >= autoRestartAt {
			out = append(out, b.autoRestart(ctx, c, st, now)...)
		}
	}
	return out
}

func (b *Bot) autoRestart(ctx context.Context, c Container, st *svcState, now time.Time) []string {
	recent := st.autoFixes[:0]
	for _, t := range st.autoFixes {
		if now.Sub(t) < time.Hour {
			recent = append(recent, t)
		}
	}
	st.autoFixes = recent
	if len(recent) >= maxAutoPerHour {
		if len(recent) == maxAutoPerHour {
			st.autoFixes = append(st.autoFixes, now) // marks the escalation as sent
			return []string{fmt.Sprintf("🚨 <b>backend</b> vẫn lỗi sau %d lần tự restart trong 1 giờ. Bot dừng tự sửa.\nXem /errors backend 30m, cân nhắc /rollback.", maxAutoPerHour)}
		}
		return nil
	}
	if !b.acting.TryLock() {
		return nil // someone is running an action; try next poll
	}
	defer b.acting.Unlock()
	st.autoFixes = append(st.autoFixes, now)
	b.audit.Record(0, "auto-restart backend", "started")
	if err := b.docker.Restart(ctx, c.Name); err != nil {
		return []string{"❌ Tự restart backend lỗi: " + esc(err.Error())}
	}
	st.badSince = time.Now() // give the restart its own 3 minutes
	if _, err := b.docker.WaitHealthy(ctx, c.Name, 3*time.Minute); err != nil {
		return []string{fmt.Sprintf("⚠️ Đã tự restart backend (lần %d/giờ) nhưng chưa healthy: %s", len(st.autoFixes), esc(err.Error()))}
	}
	return []string{fmt.Sprintf("🛠 Backend lỗi quá 3 phút, bot đã tự restart (lần %d/giờ) và backend đã healthy.", len(st.autoFixes))}
}

func (b *Bot) dailyReport(ctx context.Context) {
	if b.cfg.ReportHour < 0 {
		return
	}
	for ctx.Err() == nil {
		now := time.Now().In(b.cfg.Location)
		next := time.Date(now.Year(), now.Month(), now.Day(), b.cfg.ReportHour, 0, 0, 0, b.cfg.Location)
		if !next.After(now) {
			next = next.AddDate(0, 0, 1)
		}
		sleep(ctx, time.Until(next))
		if ctx.Err() != nil {
			return
		}
		rctx, cancel := context.WithTimeout(ctx, 2*time.Minute)
		r, err := b.cmdReport(rctx, nil)
		cancel()
		if err != nil {
			b.notify(ctx, "❌ Báo cáo ngày lỗi: "+esc(err.Error()))
			continue
		}
		b.notify(ctx, r.HTML)
	}
}

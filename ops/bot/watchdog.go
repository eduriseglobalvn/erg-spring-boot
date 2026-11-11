package main

import (
	"context"
	"fmt"
	"log"
	"sort"
	"strings"
	"time"
)

// svcState is what the watchdog remembers about one container between polls.
type svcState struct {
	badSince  time.Time // zero while healthy
	notified  bool      // a "down" message was sent for the current bad spell
	restarts  int
	image     string // empty until the first inspect
	newImage  bool   // the image changed and the new one has not been healthy yet
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
	for _, svc := range b.cfg.Required {
		if svc = strings.TrimSpace(svc); svc != "" {
			states[b.cfg.Project+"-"+svc+"-1"] = &svcState{restarts: -1}
		}
	}
	var dockerDown spell
	var pr probes
	first := true
	for ctx.Err() == nil {
		now := time.Now()
		var msgs []string
		list, err := b.docker.Containers(ctx)
		down, recovered := dockerDown.step(err != nil, now)
		if down {
			msgs = append(msgs, "🔴 <b>Bot không hỏi được Docker</b>, không theo dõi được container nào: "+esc(err.Error()))
		}
		if recovered > 0 {
			msgs = append(msgs, "✅ Docker trả lời lại sau "+durH(recovered)+".")
		}
		if err != nil {
			log.Printf("watchdog: %v", err)
		} else {
			msgs = append(msgs, b.watchOnce(ctx, list, states, now, first)...)
			first = false
		}
		// The probes are for what container health cannot see. While backend or nginx is itself reported
		// bad (a deploy, a restart) they would only repeat that message, so they wait.
		if err != nil || (healthy(list, "backend") && healthy(list, "nginx")) {
			msgs = append(msgs, b.probe(ctx, &pr, time.Now())...)
		}
		b.notify(ctx, strings.Join(msgs, "\n"))
		sleep(ctx, watchEvery)
	}
}

func healthy(list []Container, service string) bool {
	for _, c := range list {
		if c.Service == service && c.Healthy() {
			return true
		}
	}
	return false
}

// changes reports a restart by Docker's restart policy and a switch to another image (a deploy or a rollback).
func (st *svcState) changes(service string, in Inspect, first bool) []string {
	var out []string
	if st.restarts >= 0 && in.Restarts > st.restarts && !first {
		why := ""
		if in.OOMKilled {
			why = " (hết RAM)"
		}
		out = append(out, fmt.Sprintf("🔁 <b>%s</b> tự khởi động lại%s, lần thứ %d.", esc(service), why, in.Restarts))
	}
	st.restarts = in.Restarts
	if st.image != "" && in.Image != st.image {
		st.newImage = true
		out = append(out, fmt.Sprintf("🚀 <b>%s</b> đổi bản: %s → %s.", esc(service), esc(st.image), esc(in.Image)))
	}
	st.image = in.Image
	return out
}

// watchOnce compares the containers with the last poll and returns the messages to send.
func (b *Bot) watchOnce(ctx context.Context, list []Container, states map[string]*svcState, now time.Time, first bool) []string {
	var out []string
	present := map[string]bool{}
	for _, c := range list {
		present[c.Name] = true
		st := states[c.Name]
		if st == nil {
			st = &svcState{restarts: -1}
			states[c.Name] = st
		}
		if in, err := b.docker.Inspect(ctx, c.Name); err == nil {
			out = append(out, st.changes(c.Service, in, first)...)
		}
		if c.Healthy() {
			switch {
			case st.newImage:
				out = append(out, fmt.Sprintf("✅ <b>%s</b> bản mới đã chạy ổn.", esc(c.Service)))
			case st.notified:
				out = append(out, fmt.Sprintf("✅ <b>%s</b> đã ổn lại sau %s.", esc(c.Service), durH(now.Sub(st.badSince))))
			}
			st.badSince, st.notified, st.newImage = time.Time{}, false, false
			continue
		}
		if st.badSince.IsZero() {
			st.badSince = now
		}
		bad := now.Sub(st.badSince)
		if !st.notified && bad >= notifyAfter {
			st.notified = true
			if strings.Contains(c.Status, "health: starting") {
				out = append(out, fmt.Sprintf("🟡 <b>%s</b> đang khởi động, chưa healthy sau %s.", esc(c.Service), durH(bad)))
			} else {
				out = append(out, fmt.Sprintf("🔴 <b>%s</b> lỗi: %s (%s). Xem /service %s", esc(c.Service), esc(c.Status), durH(bad), esc(c.Service)))
			}
		}
		if c.Service == "backend" && bad >= autoRestartAt {
			out = append(out, b.autoRestart(ctx, c, st, now)...)
		}
	}
	return append(out, missing(states, present, now)...)
}

// missing reports containers the watchdog knows (seen before, or listed in BOT_REQUIRED_SERVICES) that Docker
// no longer lists at all. One-off `compose run` containers come and go by design and are skipped.
func missing(states map[string]*svcState, present map[string]bool, now time.Time) []string {
	var names []string
	for name, st := range states {
		if present[name] || strings.Contains(name, "-run-") {
			continue
		}
		if st.badSince.IsZero() {
			st.badSince = now
		}
		if !st.notified && now.Sub(st.badSince) >= notifyAfter {
			st.notified = true
			names = append(names, name)
		}
	}
	sort.Strings(names)
	out := make([]string, 0, len(names))
	for _, name := range names {
		out = append(out, fmt.Sprintf("🔴 Container <b>%s</b> không còn trên máy (bị xoá hoặc chưa được tạo). Xem /services.", esc(name)))
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

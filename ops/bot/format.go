package main

import (
	"fmt"
	"html"
	"math"
	"strings"
	"time"
)

func esc(s string) string { return html.EscapeString(s) }

func bytesH(v float64) string {
	if math.IsNaN(v) {
		return "—"
	}
	units := []string{"B", "KB", "MB", "GB", "TB"}
	i := 0
	for v >= 1024 && i < len(units)-1 {
		v /= 1024
		i++
	}
	if i == 0 {
		return fmt.Sprintf("%.0f B", v)
	}
	return fmt.Sprintf("%.1f %s", v, units[i])
}

func durH(d time.Duration) string {
	if d < 0 {
		d = -d
	}
	switch {
	case d < time.Minute:
		return fmt.Sprintf("%ds", int(d.Seconds()))
	case d < time.Hour:
		return fmt.Sprintf("%d phút", int(d.Minutes()))
	case d < 48*time.Hour:
		return fmt.Sprintf("%dh%02d", int(d.Hours()), int(d.Minutes())%60)
	default:
		return fmt.Sprintf("%d ngày", int(d.Hours()/24))
	}
}

func num(v float64, unit string) string {
	if math.IsNaN(v) || math.IsInf(v, 0) {
		return "—"
	}
	switch {
	case v == math.Trunc(v) && math.Abs(v) < 1e15:
		return fmt.Sprintf("%.0f%s", v, unit)
	case v >= 100:
		return fmt.Sprintf("%.0f%s", v, unit)
	case v >= 10:
		return fmt.Sprintf("%.1f%s", v, unit)
	default:
		return fmt.Sprintf("%.2f%s", v, unit)
	}
}

func ms(seconds float64) string {
	if math.IsNaN(seconds) {
		return "—"
	}
	return fmt.Sprintf("%.0f ms", seconds*1000)
}

func stateIcon(c Container) string {
	switch {
	case c.State != "running":
		return "🔴"
	case strings.Contains(c.Status, "unhealthy"):
		return "🟠"
	case strings.Contains(c.Status, "health: starting"):
		return "🟡"
	default:
		return "🟢"
	}
}

func pre(s string) string { return "<pre>" + esc(strings.TrimRight(s, "\n")) + "</pre>" }

func clip(s string, n int) string {
	r := []rune(s)
	if len(r) <= n {
		return s
	}
	return string(r[:n-1]) + "…"
}

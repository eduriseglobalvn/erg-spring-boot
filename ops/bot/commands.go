package main

import (
	"context"
	"fmt"
	"math"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
)

const backendJob = `job="erg-backend",uri!~"/actuator.*|/ready|/health.*"` // probes would drown the real API

var windows = map[string]bool{"5m": true, "15m": true, "1h": true, "6h": true, "24h": true, "7d": true}

func (b *Bot) val(ctx context.Context, expr string) float64 {
	v, ok := b.prom.Scalar(ctx, expr)
	if !ok {
		return math.NaN()
	}
	return v
}

func (b *Bot) cmdHelp(_ context.Context, _ []string) (Reply, error) {
	var s strings.Builder
	s.WriteString("<b>Bot vận hành ERG</b>\n")
	for _, c := range commandHelp {
		fmt.Fprintf(&s, "/%s — %s\n", c[0], esc(c[1]))
	}
	if b.totp != nil {
		s.WriteString("\n⚠️ Lệnh có dấu ⚠️ cần mã 6 số từ app Authenticator.")
	} else {
		s.WriteString("\n⚠️ Lệnh có dấu ⚠️ cần bấm xác nhận.")
	}
	return Reply{HTML: s.String()}, nil
}

func (b *Bot) cmdStatus(ctx context.Context, _ []string) (Reply, error) {
	var s strings.Builder
	now := time.Now().In(b.cfg.Location)
	fmt.Fprintf(&s, "🖥 <b>Server ERG</b> — %s\n", now.Format("02/01 15:04"))
	memTotal := b.val(ctx, `node_memory_MemTotal_bytes`)
	memAvail := b.val(ctx, `node_memory_MemAvailable_bytes`)
	diskSize := b.val(ctx, `node_filesystem_size_bytes{mountpoint="/"}`)
	diskAvail := b.val(ctx, `node_filesystem_avail_bytes{mountpoint="/"}`)
	fmt.Fprintf(&s, "CPU %s · load %s · RAM %s/%s (%s) · Disk %s/%s (%s) · uptime %s\n",
		num(b.val(ctx, `100 * (1 - avg(rate(node_cpu_seconds_total{mode="idle"}[5m])))`), "%"),
		num(b.val(ctx, `node_load1`), ""),
		bytesH(memTotal-memAvail), bytesH(memTotal), num(100*(memTotal-memAvail)/memTotal, "%"),
		bytesH(diskSize-diskAvail), bytesH(diskSize), num(100*(diskSize-diskAvail)/diskSize, "%"),
		durH(time.Duration(b.val(ctx, `time() - node_boot_time_seconds`))*time.Second))

	list, err := b.docker.Containers(ctx)
	if err != nil {
		fmt.Fprintf(&s, "📦 Docker: ❌ %s\n", esc(err.Error()))
	} else {
		running, bad := 0, []string{}
		for _, c := range list {
			if c.State == "running" {
				running++
			}
			if !c.Healthy() {
				bad = append(bad, stateIcon(c)+" "+c.Service)
			}
		}
		fmt.Fprintf(&s, "📦 Container %d/%d đang chạy", running, len(list))
		if len(bad) > 0 {
			fmt.Fprintf(&s, " · lỗi: %s", esc(strings.Join(bad, ", ")))
		}
		s.WriteString("\n")
	}
	image := "?"
	if in, err := b.docker.Inspect(ctx, b.cfg.Project+"-backend-1"); err == nil {
		image = in.Image + " · chạy " + durH(time.Since(parseTime(in.StartedAt)))
	}
	rps := b.val(ctx, `sum(rate(http_server_requests_seconds_count{`+backendJob+`}[5m]))`)
	errs := b.val(ctx, `sum(rate(http_server_requests_seconds_count{`+backendJob+`,status=~"5.."}[5m]))`)
	fmt.Fprintf(&s, "⚙️ Backend %s\n   %s req/s · 5xx %s · p95 %s\n", esc(image), num(rps, ""),
		num(100*safeDiv(errs, rps), "%"),
		ms(b.val(ctx, `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{`+backendJob+`}[5m])))`)))
	fmt.Fprintf(&s, "🗄 Postgres %s/%s kết nối · DB %s · Redis state %s · cache %s\n",
		num(b.val(ctx, `sum(pg_stat_activity_count)`), ""), num(b.val(ctx, `max(pg_settings_max_connections)`), ""),
		bytesH(b.val(ctx, `pg_database_size_bytes{datname="erg"}`)),
		bytesH(b.val(ctx, `redis_memory_used_bytes{job="redis-state-exporter"}`)),
		bytesH(b.val(ctx, `redis_memory_used_bytes{job="redis-cache-exporter"}`)))
	if age := b.val(ctx, `time() - erg_backup_last_success_timestamp_seconds`); !math.IsNaN(age) {
		icon := "💾"
		if age > 26*3600 {
			icon = "🚨"
		}
		fmt.Fprintf(&s, "%s Backup cuối %s trước (%s)\n", icon, durH(time.Duration(age)*time.Second),
			bytesH(b.val(ctx, `erg_backup_postgres_bytes`)))
	} else {
		s.WriteString("🚨 Chưa có số liệu backup\n")
	}
	if alerts, err := ActiveAlerts(ctx, b.httpc, b.cfg.AMURL); err == nil {
		fmt.Fprintf(&s, "🚨 Alert đang kêu: %d", len(alerts))
		for _, a := range alerts {
			fmt.Fprintf(&s, "\n   • %s (%s)", esc(a.Name), esc(a.Severity))
		}
	} else {
		fmt.Fprintf(&s, "🚨 Alertmanager: ❌ %s", esc(err.Error()))
	}
	return Reply{HTML: s.String()}, nil
}

// count rounds Prometheus increase(), which extrapolates to fractions like 9.01.
func count(v float64) string { return num(math.Round(v), "") }

func safeDiv(a, b float64) float64 {
	if b == 0 || math.IsNaN(b) {
		return math.NaN() // no traffic: show "—", not a fake 0%
	}
	return a / b
}

func parseTime(s string) time.Time {
	t, _ := time.Parse(time.RFC3339Nano, s)
	return t
}

func (b *Bot) cmdServices(ctx context.Context, _ []string) (Reply, error) {
	list, err := b.docker.Containers(ctx)
	if err != nil {
		return Reply{}, err
	}
	stats := b.docker.Stats(ctx)
	var s strings.Builder
	s.WriteString("<b>Service</b> (CPU · RAM)\n")
	for _, c := range list {
		st := stats[c.Name]
		fmt.Fprintf(&s, "%s <b>%s</b> — %s\n    %s · %s\n", stateIcon(c), esc(c.Service), esc(c.Status),
			esc(orDash(st.CPU)), esc(orDash(st.Mem)))
	}
	s.WriteString("\nChi tiết: /service &lt;tên&gt; · log: /logs &lt;tên&gt;")
	return Reply{HTML: s.String()}, nil
}

func orDash(s string) string {
	if s == "" {
		return "—"
	}
	return s
}

func (b *Bot) cmdService(ctx context.Context, args []string) (Reply, error) {
	if len(args) == 0 {
		return Reply{}, fmt.Errorf("cần tên service, ví dụ /service backend")
	}
	c, err := b.docker.Resolve(ctx, args[0])
	if err != nil {
		return Reply{}, err
	}
	in, err := b.docker.Inspect(ctx, c.Name)
	if err != nil {
		return Reply{}, err
	}
	st := b.docker.Stats(ctx)[c.Name]
	logs, _ := b.docker.Logs(ctx, c.Name, 15, "")
	health := in.Health
	if health == "" {
		health = "không có healthcheck"
	}
	var s strings.Builder
	fmt.Fprintf(&s, "%s <b>%s</b> (%s)\n", stateIcon(c), esc(c.Service), esc(c.Name))
	fmt.Fprintf(&s, "Trạng thái: %s · health: %s\nImage: %s\nChạy từ: %s (%s trước) · restart: %d lần",
		esc(in.Status), esc(health), esc(in.Image), parseTime(in.StartedAt).In(b.cfg.Location).Format("02/01 15:04"),
		durH(time.Since(parseTime(in.StartedAt))), in.Restarts)
	if in.OOMKilled {
		s.WriteString(" · ⚠️ bị kill vì hết RAM")
	}
	if in.Status != "running" {
		fmt.Fprintf(&s, " · exit %d", in.ExitCode)
	}
	fmt.Fprintf(&s, "\nCPU %s · RAM %s (%s) · Net %s · Disk IO %s · PIDs %s\n",
		esc(orDash(st.CPU)), esc(orDash(st.Mem)), esc(orDash(st.MemPerc)), esc(orDash(st.Net)), esc(orDash(st.Block)), esc(orDash(st.PIDs)))
	if c.Service == "backend" {
		fmt.Fprintf(&s, "Heap old-gen %s · Hikari active %s / pending %s\n",
			num(100*b.val(ctx, `max(jvm_memory_used_bytes{area="heap",id="G1 Old Gen"} / jvm_memory_max_bytes{area="heap",id="G1 Old Gen"})`), "%"),
			num(b.val(ctx, `sum(hikaricp_connections_active)`), ""), num(b.val(ctx, `sum(hikaricp_connections_pending)`), ""))
	}
	s.WriteString("\n15 dòng log cuối:\n" + pre(Redact(stripTimestamps(logs))))
	return Reply{HTML: s.String()}, nil
}

var durArg = regexp.MustCompile(`^\d+[smhd]$`)

// parseLogArgs reads "[service] [lines] [since] [filter…]" in any order after the service.
func parseLogArgs(args []string, defLines int) (lines int, since, filter string) {
	lines = defLines
	var words []string
	for _, a := range args {
		switch {
		case durArg.MatchString(a):
			since = a
			if strings.HasSuffix(a, "d") {
				n, _ := strconv.Atoi(strings.TrimSuffix(a, "d"))
				since = strconv.Itoa(n*24) + "h"
			}
		case isNumber(a):
			lines, _ = strconv.Atoi(a)
		default:
			words = append(words, a)
		}
	}
	lines = max(10, min(lines, 20000))
	return lines, since, strings.Join(words, " ")
}

func isNumber(s string) bool {
	_, err := strconv.Atoi(s)
	return err == nil
}

func (b *Bot) cmdLogs(ctx context.Context, args []string) (Reply, error) {
	if len(args) == 0 {
		return Reply{}, fmt.Errorf("cần tên service, ví dụ /logs backend 300 30m")
	}
	c, err := b.docker.Resolve(ctx, args[0])
	if err != nil {
		return Reply{}, err
	}
	lines, since, filter := parseLogArgs(args[1:], 200)
	raw, err := b.docker.Logs(ctx, c.Name, lines, since)
	if err != nil {
		return Reply{}, err
	}
	return b.logReply(c, raw, filter, nil, fmt.Sprintf("%d dòng cuối", lines), since)
}

var errorLine = regexp.MustCompile(`(?i)\b(error|warn|warning|exception|fatal|panic|caused by)\b|" 5\d\d |level=(error|warn)`)

func (b *Bot) cmdErrors(ctx context.Context, args []string) (Reply, error) {
	name := "backend"
	if len(args) > 0 && !durArg.MatchString(args[0]) && !isNumber(args[0]) {
		name, args = args[0], args[1:]
	}
	c, err := b.docker.Resolve(ctx, name)
	if err != nil {
		return Reply{}, err
	}
	_, since, filter := parseLogArgs(args, 0)
	if since == "" {
		since = "1h"
	}
	raw, err := b.docker.Logs(ctx, c.Name, 20000, since)
	if err != nil {
		return Reply{}, err
	}
	return b.logReply(c, raw, filter, errorLine, "dòng lỗi/cảnh báo", since)
}

func (b *Bot) logReply(c Container, raw, filter string, only *regexp.Regexp, what, since string) (Reply, error) {
	var kept []string
	lower := strings.ToLower(filter)
	for _, line := range strings.Split(strings.TrimRight(raw, "\n"), "\n") {
		if line == "" || (only != nil && !only.MatchString(line)) || (filter != "" && !strings.Contains(strings.ToLower(line), lower)) {
			continue
		}
		kept = append(kept, line)
	}
	title := fmt.Sprintf("📜 <b>%s</b> — %s", esc(c.Service), what)
	if since != "" {
		title += " trong " + esc(since)
	}
	if filter != "" {
		title += " chứa “" + esc(filter) + "”"
	}
	if len(kept) == 0 {
		return Reply{HTML: title + ": không có dòng nào."}, nil
	}
	body := Redact(strings.Join(kept, "\n"))
	title += fmt.Sprintf(" (%d dòng)", len(kept))
	if len(body) < 3300 {
		return Reply{HTML: title + "\n" + pre(stripTimestamps(body))}, nil
	}
	name := fmt.Sprintf("%s-%s.log", c.Service, time.Now().UTC().Format("20060102-150405"))
	return Reply{HTML: title, Doc: []byte(body + "\n"), DocName: name}, nil
}

var tsPrefix = regexp.MustCompile(`(?m)^\d{4}-\d\d-\d\dT(\d\d:\d\d:\d\d)\.\d+Z (\d{4}-\d\d-\d\d[T ])?`)

// stripTimestamps shortens Docker's timestamp to HH:MM:SS, or drops it when the line carries its own.
func stripTimestamps(s string) string {
	return tsPrefix.ReplaceAllStringFunc(s, func(m string) string {
		if sub := tsPrefix.FindStringSubmatch(m); sub[2] == "" {
			return sub[1] + " "
		}
		return m[strings.LastIndexByte(m[:len(m)-1], ' ')+1:]
	})
}

func (b *Bot) cmdTop(ctx context.Context, args []string) (Reply, error) {
	w := "1h"
	if len(args) > 0 {
		if !windows[args[0]] {
			return Reply{}, fmt.Errorf("khoảng thời gian nhận: 5m, 15m, 1h, 6h, 24h, 7d")
		}
		w = args[0]
	}
	var s strings.Builder
	fmt.Fprintf(&s, "📈 <b>API trong %s</b>\n", w)
	total := b.val(ctx, `sum(increase(http_server_requests_seconds_count{`+backendJob+`}[`+w+`]))`)
	fmt.Fprintf(&s, "Tổng %s request · 5xx %s\n", count(total),
		count(b.val(ctx, `sum(increase(http_server_requests_seconds_count{`+backendJob+`,status=~"5.."}[`+w+`]))`)))
	section := func(title, expr string, format func(Sample) string) {
		rows, err := b.prom.Query(ctx, expr)
		fmt.Fprintf(&s, "\n<b>%s</b>\n", title)
		if err != nil {
			fmt.Fprintf(&s, "❌ %s\n", esc(err.Error()))
			return
		}
		var lines []string
		for _, r := range rows {
			if r.Value < 0.0005 || math.IsNaN(r.Value) {
				continue
			}
			lines = append(lines, format(r))
		}
		if len(lines) == 0 {
			s.WriteString("(không có)\n")
			return
		}
		s.WriteString(pre(strings.Join(lines, "\n")) + "\n")
	}
	route := func(r Sample) string { return clip(r.Labels["method"]+" "+r.Labels["uri"], 52) }
	section("Gọi nhiều nhất", `topk(10, sum by (method, uri) (increase(http_server_requests_seconds_count{`+backendJob+`}[`+w+`])))`,
		func(r Sample) string { return fmt.Sprintf("%7.0f  %s", r.Value, route(r)) })
	section("Chậm nhất (p95)", `topk(10, histogram_quantile(0.95, sum by (le, method, uri) (rate(http_server_requests_seconds_bucket{`+backendJob+`}[`+w+`]))))`,
		func(r Sample) string { return fmt.Sprintf("%7s  %s", ms(r.Value), route(r)) })
	section("Lỗi 5xx nhiều nhất", `topk(10, sum by (method, uri, status) (increase(http_server_requests_seconds_count{`+backendJob+`,status=~"5.."}[`+w+`])))`,
		func(r Sample) string { return fmt.Sprintf("%5.0f %s %s", r.Value, r.Labels["status"], route(r)) })
	section("Lỗi 4xx nhiều nhất", `topk(5, sum by (method, uri, status) (increase(http_server_requests_seconds_count{`+backendJob+`,status=~"4.."}[`+w+`])))`,
		func(r Sample) string { return fmt.Sprintf("%5.0f %s %s", r.Value, r.Labels["status"], route(r)) })
	section("GraphQL operation", `topk(8, sum by (operation) (increase(graphql_operation_duration_seconds_count[`+w+`])))`,
		func(r Sample) string { return fmt.Sprintf("%7.0f  %s", r.Value, clip(r.Labels["operation"], 52)) })
	return Reply{HTML: s.String()}, nil
}

func (b *Bot) cmdDB(ctx context.Context, _ []string) (Reply, error) {
	var s strings.Builder
	up := func(expr string) string {
		if b.val(ctx, expr) == 1 {
			return "🟢"
		}
		return "🔴"
	}
	fmt.Fprintf(&s, "%s <b>Postgres</b>: %s/%s kết nối · DB erg %s\n", up(`pg_up`),
		num(b.val(ctx, `sum(pg_stat_activity_count)`), ""), num(b.val(ctx, `max(pg_settings_max_connections)`), ""),
		bytesH(b.val(ctx, `pg_database_size_bytes{datname="erg"}`)))
	fmt.Fprintf(&s, "   commit %s/s · rollback %s/s · deadlock 24h %s\n",
		num(b.val(ctx, `sum(rate(pg_stat_database_xact_commit{datname="erg"}[5m]))`), ""),
		num(b.val(ctx, `sum(rate(pg_stat_database_xact_rollback{datname="erg"}[5m]))`), ""),
		num(b.val(ctx, `sum(increase(pg_stat_database_deadlocks{datname="erg"}[24h]))`), ""))
	fmt.Fprintf(&s, "🔌 <b>Hikari</b>: active %s · idle %s · pending %s · max %s\n",
		num(b.val(ctx, `sum(hikaricp_connections_active)`), ""), num(b.val(ctx, `sum(hikaricp_connections_idle)`), ""),
		num(b.val(ctx, `sum(hikaricp_connections_pending)`), ""), num(b.val(ctx, `sum(hikaricp_connections_max)`), ""))
	for _, r := range []struct{ name, job string }{{"Redis state", "redis-state-exporter"}, {"Redis cache", "redis-cache-exporter"}} {
		sel := `{job="` + r.job + `"}`
		hits := b.val(ctx, `sum(rate(redis_keyspace_hits_total`+sel+`[1h]))`)
		miss := b.val(ctx, `sum(rate(redis_keyspace_misses_total`+sel+`[1h]))`)
		fmt.Fprintf(&s, "%s <b>%s</b>: %s/%s · %s key · %s client · hit 1h %s\n", up(`redis_up`+sel), r.name,
			bytesH(b.val(ctx, `redis_memory_used_bytes`+sel)), bytesH(b.val(ctx, `redis_memory_max_bytes`+sel)),
			num(b.val(ctx, `sum(redis_db_keys`+sel+`)`), ""), num(b.val(ctx, `redis_connected_clients`+sel), ""),
			num(100*safeDiv(hits, hits+miss), "%"))
	}
	rows, err := b.prom.Query(ctx, `sum by (outcome) (increase(erg_cache_operations_total[1h]))`)
	if err == nil && len(rows) > 0 {
		s.WriteString("🧠 <b>Cache ứng dụng 1h</b>: ")
		parts := make([]string, 0, len(rows))
		for _, r := range rows {
			parts = append(parts, fmt.Sprintf("%s %.0f", esc(r.Labels["outcome"]), r.Value))
		}
		s.WriteString(strings.Join(parts, " · ") + "\n")
	}
	fmt.Fprintf(&s, "📨 <b>Outbox thông báo</b>: pending %s · retry %s · dead %s",
		num(b.val(ctx, `max(notifications_outbox_pending)`), ""), num(b.val(ctx, `max(notifications_outbox_retry)`), ""),
		num(b.val(ctx, `max(notifications_outbox_dead)`), ""))
	return Reply{HTML: s.String()}, nil
}

func (b *Bot) cmdAlerts(ctx context.Context, _ []string) (Reply, error) {
	alerts, err := ActiveAlerts(ctx, b.httpc, b.cfg.AMURL)
	if err != nil {
		return Reply{}, err
	}
	if len(alerts) == 0 {
		return Reply{HTML: "✅ Không có alert nào đang kêu."}, nil
	}
	var s strings.Builder
	fmt.Fprintf(&s, "🚨 <b>%d alert đang kêu</b>\n", len(alerts))
	for _, a := range alerts {
		fmt.Fprintf(&s, "• <b>%s</b> (%s) từ %s trước\n  %s\n", esc(a.Name), esc(a.Severity), durH(time.Since(a.Since)), esc(a.Summary))
	}
	return Reply{HTML: s.String()}, nil
}

type fileInfo struct {
	name string
	size int64
	mod  time.Time
}

func listFiles(dir string, keep func(string) bool) []fileInfo {
	entries, _ := os.ReadDir(dir)
	var out []fileInfo
	for _, e := range entries {
		if e.IsDir() || !keep(e.Name()) {
			continue
		}
		if info, err := e.Info(); err == nil {
			out = append(out, fileInfo{e.Name(), info.Size(), info.ModTime()})
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].mod.After(out[j].mod) })
	return out
}

func (b *Bot) cmdBackups(ctx context.Context, _ []string) (Reply, error) {
	var s strings.Builder
	s.WriteString("💾 <b>Backup</b>\n")
	if age := b.val(ctx, `time() - erg_backup_last_success_timestamp_seconds`); !math.IsNaN(age) {
		fmt.Fprintf(&s, "Lần thành công cuối: %s trước\n", durH(time.Duration(age)*time.Second))
	}
	if cron, err := run(ctx, 5*time.Second, "crontab", "-l"); err == nil {
		for _, l := range strings.Split(cron, "\n") {
			if f := strings.Fields(l); len(f) > 5 && strings.Contains(l, "backup.sh") && !strings.HasPrefix(f[0], "#") {
				fmt.Fprintf(&s, "Lịch cron (giờ UTC): <code>%s</code>\n", esc(strings.Join(f[:5], " ")))
			}
		}
	}
	for _, dir := range b.cfg.BackupDirs {
		files := listFiles(dir, func(n string) bool {
			return strings.HasSuffix(n, ".sql.gz") || strings.HasSuffix(n, ".dump")
		})
		var total int64
		for _, f := range files {
			total += f.size
		}
		fmt.Fprintf(&s, "\n<b>%s</b> — %d file, %s\n", esc(dir), len(files), bytesH(float64(total)))
		var rows []string
		for i, f := range files {
			if i == 8 {
				rows = append(rows, fmt.Sprintf("… và %d file cũ hơn", len(files)-8))
				break
			}
			rows = append(rows, fmt.Sprintf("%s  %9s  %s", f.mod.In(b.cfg.Location).Format("02/01 15:04"), bytesH(float64(f.size)), clip(f.name, 40)))
		}
		if len(rows) > 0 {
			s.WriteString(pre(strings.Join(rows, "\n")) + "\n")
		}
	}
	if tail, err := run(ctx, 5*time.Second, "tail", "-n", "200", b.cfg.BackupLog); err == nil {
		last := ""
		for _, l := range strings.Split(tail, "\n") {
			if strings.Contains(l, "Backup Completed") || strings.Contains(l, "Starting ERG") || strings.Contains(strings.ToLower(l), "error") {
				last = l
			}
		}
		if last != "" {
			fmt.Fprintf(&s, "Log backup gần nhất: <code>%s</code>\n", esc(clip(last, 160)))
		}
	}
	s.WriteString("\nChạy backup ngay: /backup")
	return Reply{HTML: s.String()}, nil
}

func (b *Bot) cmdDisk(ctx context.Context, _ []string) (Reply, error) {
	df, _ := run(ctx, 10*time.Second, "df", "-h", "/")
	ddf, _ := run(ctx, 30*time.Second, "docker", "system", "df")
	var sizes []string
	for _, dir := range append([]string{filepath.Join(b.cfg.DockerDir, "..", "backup")}, b.cfg.BackupDirs...) {
		if out, err := run(ctx, 20*time.Second, "du", "-sh", dir); err == nil {
			sizes = append(sizes, strings.TrimSpace(out))
		}
	}
	return Reply{HTML: "💽 <b>Ổ đĩa</b>\n" + pre(df) + "\n<b>Docker</b>\n" + pre(ddf) + "\n<b>Backup</b>\n" + pre(strings.Join(sizes, "\n")) +
		"\nDọn image/build cache cũ: /prune"}, nil
}

func (b *Bot) cmdReport(ctx context.Context, _ []string) (Reply, error) {
	status, _ := b.cmdStatus(ctx, nil)
	var s strings.Builder
	s.WriteString("📊 <b>Báo cáo 24 giờ</b>\n\n" + status.HTML + "\n\n")
	total := b.val(ctx, `sum(increase(http_server_requests_seconds_count{`+backendJob+`}[24h]))`)
	errs := b.val(ctx, `sum(increase(http_server_requests_seconds_count{`+backendJob+`,status=~"5.."}[24h]))`)
	logins := b.val(ctx, `sum(increase(http_server_requests_seconds_count{uri="/api/v1/auth/login",status="200"}[24h]))`)
	failed := b.val(ctx, `sum(increase(http_server_requests_seconds_count{uri="/api/v1/auth/login",status=~"4.."}[24h]))`)
	fmt.Fprintf(&s, "<b>24h qua</b>: %s request · 5xx %s · đăng nhập OK %s / lỗi %s · CPU đỉnh %s · RAM trống thấp nhất %s\n",
		count(total), count(errs), count(logins), count(failed),
		num(b.val(ctx, `max_over_time((100 * (1 - avg(rate(node_cpu_seconds_total{mode="idle"}[5m]))))[24h:5m])`), "%"),
		bytesH(b.val(ctx, `min_over_time(node_memory_MemAvailable_bytes[24h])`)))
	rows, err := b.prom.Query(ctx, `topk(5, sum by (method, uri) (increase(http_server_requests_seconds_count{`+backendJob+`}[24h])))`)
	if err == nil && len(rows) > 0 {
		var lines []string
		for _, r := range rows {
			if r.Value < 0.5 {
				continue
			}
			lines = append(lines, fmt.Sprintf("%7.0f  %s", r.Value, clip(r.Labels["method"]+" "+r.Labels["uri"], 50)))
		}
		s.WriteString("Top API:\n" + pre(strings.Join(lines, "\n")) + "\n")
	}
	if list, err := b.docker.Containers(ctx); err == nil {
		var restarted []string
		for _, c := range list {
			if in, err := b.docker.Inspect(ctx, c.Name); err == nil && time.Since(parseTime(in.StartedAt)) < 24*time.Hour {
				restarted = append(restarted, c.Service)
			}
		}
		if len(restarted) > 0 {
			fmt.Fprintf(&s, "Khởi động lại trong 24h: %s\n", esc(strings.Join(restarted, ", ")))
		}
	}
	return Reply{HTML: s.String()}, nil
}

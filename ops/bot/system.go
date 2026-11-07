package main

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"os/exec"
	"sort"
	"strconv"
	"strings"
	"time"
)

// run executes a fixed program with separate arguments (never through a shell), so user input cannot inject commands.
func run(ctx context.Context, timeout time.Duration, name string, args ...string) (string, error) {
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	out, err := exec.CommandContext(ctx, name, args...).CombinedOutput()
	if ctx.Err() == context.DeadlineExceeded {
		return string(out), fmt.Errorf("%s timed out after %s", name, timeout)
	}
	return string(out), err
}

type Container struct {
	Name, Service, State, Status, Image string
}

func (c Container) Healthy() bool {
	return c.State == "running" && !strings.Contains(c.Status, "unhealthy") && !strings.Contains(c.Status, "health: starting")
}

type Docker struct{ project string }

func (d Docker) Containers(ctx context.Context) ([]Container, error) {
	out, err := run(ctx, 20*time.Second, "docker", "ps", "-a",
		"--filter", "label=com.docker.compose.project="+d.project, "--format", "{{json .}}")
	if err != nil {
		return nil, fmt.Errorf("docker ps: %v: %s", err, firstLine(out))
	}
	var list []Container
	sc := bufio.NewScanner(strings.NewReader(out))
	for sc.Scan() {
		var row struct{ Names, State, Status, Image, Labels string }
		if json.Unmarshal(sc.Bytes(), &row) != nil {
			continue
		}
		service := row.Names
		for _, l := range strings.Split(row.Labels, ",") {
			if v, ok := strings.CutPrefix(l, "com.docker.compose.service="); ok {
				service = v
			}
		}
		list = append(list, Container{Name: row.Names, Service: service, State: row.State, Status: row.Status, Image: row.Image})
	}
	sort.Slice(list, func(i, j int) bool { return list[i].Service < list[j].Service })
	return list, nil
}

// Resolve maps a user-typed service name to a container of this project; anything else is refused.
func (d Docker) Resolve(ctx context.Context, name string) (Container, error) {
	list, err := d.Containers(ctx)
	if err != nil {
		return Container{}, err
	}
	name = strings.ToLower(strings.TrimSpace(name))
	for _, c := range list {
		if c.Service == name || c.Name == name {
			return c, nil
		}
	}
	names := make([]string, 0, len(list))
	for _, c := range list {
		names = append(names, c.Service)
	}
	return Container{}, fmt.Errorf("không có service %q. Có: %s", name, strings.Join(names, ", "))
}

type Stat struct{ CPU, Mem, MemPerc, Net, Block, PIDs string }

func (d Docker) Stats(ctx context.Context) map[string]Stat {
	out, err := run(ctx, 30*time.Second, "docker", "stats", "--no-stream", "--format", "{{json .}}")
	stats := map[string]Stat{}
	if err != nil {
		return stats
	}
	sc := bufio.NewScanner(strings.NewReader(out))
	for sc.Scan() {
		var row struct{ Name, CPUPerc, MemUsage, MemPerc, NetIO, BlockIO, PIDs string }
		if json.Unmarshal(sc.Bytes(), &row) == nil {
			stats[row.Name] = Stat{row.CPUPerc, row.MemUsage, row.MemPerc, row.NetIO, row.BlockIO, row.PIDs}
		}
	}
	return stats
}

type Inspect struct {
	Status, Health, StartedAt, Image string
	Restarts                         int
	OOMKilled                        bool
	ExitCode                         int
}

func (d Docker) Inspect(ctx context.Context, name string) (Inspect, error) {
	out, err := run(ctx, 15*time.Second, "docker", "inspect", "--format",
		"{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}|{{.State.StartedAt}}|{{.RestartCount}}|{{.Config.Image}}|{{.State.OOMKilled}}|{{.State.ExitCode}}", name)
	if err != nil {
		return Inspect{}, fmt.Errorf("docker inspect: %s", firstLine(out))
	}
	f := strings.Split(strings.TrimSpace(out), "|")
	if len(f) < 7 {
		return Inspect{}, fmt.Errorf("docker inspect: unexpected output")
	}
	restarts, _ := strconv.Atoi(f[3])
	exit, _ := strconv.Atoi(f[6])
	return Inspect{Status: f[0], Health: f[1], StartedAt: f[2], Restarts: restarts, Image: f[4], OOMKilled: f[5] == "true", ExitCode: exit}, nil
}

func (d Docker) Logs(ctx context.Context, name string, tail int, since string) (string, error) {
	args := []string{"logs", "--timestamps", "--tail", strconv.Itoa(tail)}
	if since != "" {
		args = append(args, "--since", since)
	}
	out, err := run(ctx, 30*time.Second, "docker", append(args, name)...)
	if err != nil {
		return "", fmt.Errorf("docker logs: %s", firstLine(out))
	}
	return out, nil
}

func (d Docker) Restart(ctx context.Context, name string) error {
	out, err := run(ctx, 90*time.Second, "docker", "restart", "-t", "30", name)
	if err != nil {
		return fmt.Errorf("docker restart: %s", firstLine(out))
	}
	return nil
}

// WaitHealthy polls until the container is healthy (or running, when it has no health check).
func (d Docker) WaitHealthy(ctx context.Context, name string, limit time.Duration) (Inspect, error) {
	deadline := time.Now().Add(limit)
	for {
		in, err := d.Inspect(ctx, name)
		if err == nil && in.Status == "running" && (in.Health == "" || in.Health == "healthy") {
			return in, nil
		}
		if time.Now().After(deadline) {
			return in, fmt.Errorf("%s chưa healthy sau %s (trạng thái %s %s)", name, limit, in.Status, in.Health)
		}
		select {
		case <-ctx.Done():
			return in, ctx.Err()
		case <-time.After(3 * time.Second):
		}
	}
}

type Prom struct {
	base string
	http *http.Client
}

type Sample struct {
	Labels map[string]string
	Value  float64
}

func (p Prom) Query(ctx context.Context, expr string) ([]Sample, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, p.base+"/api/v1/query?query="+url.QueryEscape(expr), nil)
	if err != nil {
		return nil, err
	}
	resp, err := p.http.Do(req)
	if err != nil {
		return nil, fmt.Errorf("prometheus: %v", err)
	}
	defer resp.Body.Close()
	var body struct {
		Status string `json:"status"`
		Error  string `json:"error"`
		Data   struct {
			Result []struct {
				Metric map[string]string `json:"metric"`
				Value  [2]any            `json:"value"`
			} `json:"result"`
		} `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return nil, fmt.Errorf("prometheus: %v", err)
	}
	if body.Status != "success" {
		return nil, fmt.Errorf("prometheus: %s", body.Error)
	}
	out := make([]Sample, 0, len(body.Data.Result))
	for _, r := range body.Data.Result {
		s, _ := r.Value[1].(string)
		v, err := strconv.ParseFloat(s, 64)
		if err != nil {
			continue
		}
		out = append(out, Sample{Labels: r.Metric, Value: v})
	}
	sort.SliceStable(out, func(i, j int) bool { return out[i].Value > out[j].Value })
	return out, nil
}

// Scalar returns the first value of expr, or NaN-free ok=false when there is none.
func (p Prom) Scalar(ctx context.Context, expr string) (float64, bool) {
	s, err := p.Query(ctx, expr)
	if err != nil || len(s) == 0 {
		return 0, false
	}
	return s[0].Value, true
}

type Alert struct {
	Name, Severity, Summary string
	Since                   time.Time
}

func ActiveAlerts(ctx context.Context, client *http.Client, base string) ([]Alert, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, base+"/api/v2/alerts?active=true&silenced=false&inhibited=false", nil)
	if err != nil {
		return nil, err
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("alertmanager: %v", err)
	}
	defer resp.Body.Close()
	var raw []struct {
		Labels      map[string]string `json:"labels"`
		Annotations map[string]string `json:"annotations"`
		StartsAt    time.Time         `json:"startsAt"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&raw); err != nil {
		return nil, fmt.Errorf("alertmanager: %v", err)
	}
	out := make([]Alert, 0, len(raw))
	for _, a := range raw {
		out = append(out, Alert{a.Labels["alertname"], a.Labels["severity"], a.Annotations["summary"], a.StartsAt})
	}
	return out, nil
}

func firstLine(s string) string {
	s = strings.TrimSpace(s)
	if i := bytes.IndexByte([]byte(s), '\n'); i >= 0 {
		s = s[:i]
	}
	if len(s) > 300 {
		s = s[:300]
	}
	return s
}

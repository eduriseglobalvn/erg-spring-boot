package main

import (
	"context"
	"strings"
	"testing"
	"time"
)

func TestTOTPMatchesRFC6238(t *testing.T) {
	key, err := decodeBase32("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
	if err != nil {
		t.Fatal(err)
	}
	if got := totpCode(key, 59/30); got != "287082" {
		t.Fatalf("code = %s, want 287082", got)
	}
}

func TestTOTPRefusesReuseAndLocks(t *testing.T) {
	v := NewTOTP("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
	now := time.Unix(1_800_000_000, 0)
	code := totpCode(v.key, uint64(now.Unix()/30))
	if err := v.Verify(code, now); err != nil {
		t.Fatalf("first use: %v", err)
	}
	if v.Verify(code, now) == nil {
		t.Fatal("same code accepted twice")
	}
	for i := 0; i < 5; i++ {
		_ = v.Verify("000000", now)
	}
	next := totpCode(v.key, uint64(now.Unix()/30)+1)
	if v.Verify(next, now.Add(30*time.Second)) == nil {
		t.Fatal("valid code accepted while locked")
	}
}

func TestRedactRemovesSecretsAndPII(t *testing.T) {
	in := `Authorization: Bearer abcdefghijklmnop password=hunter22 jdbc:postgresql://erg:s3cret@db/erg ` +
		`user a.b@erg.edu.vn phone 0912345678 eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJl`
	out := Redact(in)
	for _, leak := range []string{"abcdefghijklmnop", "hunter22", "s3cret", "a.b@erg.edu.vn", "0912345678", "eyJzdWIiOiIxMjMifQ"} {
		if strings.Contains(out, leak) {
			t.Errorf("leaked %q in %q", leak, out)
		}
	}
}

func TestSplitMessageKeepsPreBalanced(t *testing.T) {
	body := "<pre>" + strings.Repeat("line of log text\n", 600) + "</pre>"
	parts := splitMessage(body, 3900)
	if len(parts) < 2 {
		t.Fatalf("expected several parts, got %d", len(parts))
	}
	for i, p := range parts {
		if len(p) > 4096 {
			t.Errorf("part %d too long: %d", i, len(p))
		}
		if strings.Count(p, "<pre>") != strings.Count(p, "</pre>") {
			t.Errorf("part %d has unbalanced <pre>", i)
		}
	}
}

func TestParseLogArgs(t *testing.T) {
	lines, since, filter := parseLogArgs([]string{"300", "2d", "Login", "failed"}, 200)
	if lines != 300 || since != "48h" || filter != "Login failed" {
		t.Fatalf("got %d %q %q", lines, since, filter)
	}
	if lines, _, _ := parseLogArgs([]string{"999999"}, 200); lines != 20000 {
		t.Fatalf("lines not capped: %d", lines)
	}
}

func TestSwapImagesOnlyTouchesImageLines(t *testing.T) {
	env := "DB_PASSWORD=x\nERG_APP_IMAGE=erg-spring:new-jvm\nERG_ROLLBACK_IMAGE=erg-spring:old-jvm\nOTHER=1\n"
	got := swapImages(env)
	want := "DB_PASSWORD=x\nERG_APP_IMAGE=erg-spring:old-jvm\nERG_ROLLBACK_IMAGE=erg-spring:new-jvm\nOTHER=1\n"
	if got != want {
		t.Fatalf("got %q", got)
	}
}

func TestWatchdogNotifiesOnceAndOnRecovery(t *testing.T) {
	b := &Bot{audit: &Audit{path: t.TempDir() + "/a.log"}}
	states := map[string]*svcState{"x": {restarts: 0}} // restarts preset so Inspect (no docker here) is not needed
	t0 := time.Now()
	down := []Container{{Name: "x", Service: "redis", State: "exited", Status: "Exited (1)"}}
	if msgs := b.watchOnce(context.Background(), down, states, t0, false); len(msgs) != 0 {
		t.Fatalf("notified too early: %v", msgs)
	}
	if msgs := b.watchOnce(context.Background(), down, states, t0.Add(61*time.Second), false); len(msgs) != 1 {
		t.Fatalf("want one down message, got %v", msgs)
	}
	if msgs := b.watchOnce(context.Background(), down, states, t0.Add(90*time.Second), false); len(msgs) != 0 {
		t.Fatalf("repeated down message: %v", msgs)
	}
	up := []Container{{Name: "x", Service: "redis", State: "running", Status: "Up 5s"}}
	if msgs := b.watchOnce(context.Background(), up, states, t0.Add(120*time.Second), false); len(msgs) != 1 || !strings.Contains(msgs[0], "ổn lại") {
		t.Fatalf("want recovery message, got %v", msgs)
	}
}

func TestStripTimestamps(t *testing.T) {
	in := "2026-11-05T05:31:10.123456789Z WARNING: x\n2026-11-05T07:03:51.006Z 2026-11-05T07:03:51.006Z  WARN 1 y"
	want := "05:31:10 WARNING: x\n2026-11-05T07:03:51.006Z  WARN 1 y"
	if got := stripTimestamps(in); got != want {
		t.Fatalf("got %q", got)
	}
}

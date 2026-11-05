package main

import (
	"crypto/hmac"
	"crypto/sha1"
	"encoding/base32"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"os"
	"regexp"
	"strings"
	"sync"
	"time"
)

func decodeBase32(secret string) ([]byte, error) {
	return base32.StdEncoding.WithPadding(base32.NoPadding).DecodeString(strings.TrimRight(secret, "="))
}

// totpCode is RFC 6238 (SHA-1, 30 s, 6 digits), the default every authenticator app uses.
func totpCode(key []byte, counter uint64) string {
	var msg [8]byte
	binary.BigEndian.PutUint64(msg[:], counter)
	mac := hmac.New(sha1.New, key)
	mac.Write(msg[:])
	sum := mac.Sum(nil)
	off := sum[len(sum)-1] & 0x0f
	bin := binary.BigEndian.Uint32(sum[off:off+4]) & 0x7fffffff
	return fmt.Sprintf("%06d", bin%1_000_000)
}

// TOTP verifies codes, refuses a code twice, and locks after repeated failures.
type TOTP struct {
	mu       sync.Mutex
	key      []byte
	lastUsed uint64
	failures int
	lockedTo time.Time
}

func NewTOTP(secret string) *TOTP {
	if secret == "" {
		return nil
	}
	key, _ := decodeBase32(secret)
	return &TOTP{key: key}
}

func (t *TOTP) Verify(code string, now time.Time) error {
	t.mu.Lock()
	defer t.mu.Unlock()
	if now.Before(t.lockedTo) {
		return fmt.Errorf("sai mã quá nhiều lần, khoá tới %s", t.lockedTo.Format("15:04"))
	}
	code = strings.TrimSpace(code)
	step := uint64(now.Unix() / 30)
	for _, c := range []uint64{step - 1, step, step + 1} {
		if c > t.lastUsed && hmac.Equal([]byte(totpCode(t.key, c)), []byte(code)) {
			t.lastUsed, t.failures = c, 0
			return nil
		}
	}
	t.failures++
	if t.failures >= 5 {
		t.failures, t.lockedTo = 0, now.Add(15*time.Minute)
		return fmt.Errorf("sai mã 5 lần, khoá thao tác 15 phút")
	}
	return fmt.Errorf("mã không đúng hoặc đã dùng")
}

var redactions = []struct {
	re   *regexp.Regexp
	with string
}{
	{regexp.MustCompile(`eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{4,}`), "<jwt>"},
	{regexp.MustCompile(`(?i)(bearer\s+)[A-Za-z0-9._~+/=-]{8,}`), "${1}***"},
	{regexp.MustCompile(`://([^:/@\s]*):([^@/\s]+)@`), "://${1}:***@"},
	{regexp.MustCompile(`(?i)\b(password|passwd|pwd|secret|token|api[_-]?key|authorization|credential)(["']?\s*[:=]\s*["']?)[^\s"',;&}]+`), "${1}${2}***"},
	{regexp.MustCompile(`[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}`), "<email>"},
	{regexp.MustCompile(`(^|[^\d])(?:\+84|0)\d{9,10}\b`), "${1}<phone>"},
}

// Redact strips tokens, credentials and personal data before log text leaves the server for Telegram.
func Redact(s string) string {
	for _, r := range redactions {
		s = r.re.ReplaceAllString(s, r.with)
	}
	return s
}

type Audit struct {
	mu   sync.Mutex
	path string
}

func (a *Audit) Record(user int64, command, result string) {
	a.mu.Lock()
	defer a.mu.Unlock()
	line, _ := json.Marshal(map[string]any{
		"at": time.Now().UTC().Format(time.RFC3339), "user": user, "command": command, "result": result,
	})
	f, err := os.OpenFile(a.path, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o600)
	if err != nil {
		return
	}
	defer f.Close()
	_, _ = f.Write(append(line, '\n'))
}

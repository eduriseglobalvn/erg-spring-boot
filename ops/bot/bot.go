package main

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"log"
	"net/http"
	"regexp"
	"strings"
	"sync"
	"time"
)

// Reply is what a command sends back: HTML text and, for long output, a file.
type Reply struct {
	HTML    string
	Doc     []byte
	DocName string
}

// Action is a state-changing command waiting for confirmation (button, or TOTP code when configured).
type Action struct {
	ID      string
	Label   string
	Run     func(ctx context.Context) (string, error)
	Expires time.Time
	Chat    int64
}

type Bot struct {
	cfg    Config
	tg     *TG
	docker Docker
	prom   Prom
	httpc  *http.Client
	totp   *TOTP
	audit  *Audit

	mu      sync.Mutex
	pending map[int64]*Action
	acting  sync.Mutex // one state change at a time

	readers map[string]func(context.Context, []string) (Reply, error)
	actions map[string]func(context.Context, []string) (*Action, error)
}

var commandHelp = [][2]string{
	{"status", "Tổng quan server: CPU, RAM, disk, backend, DB, backup, alert"},
	{"services", "Trạng thái, CPU, RAM của từng service"},
	{"service", "Chi tiết một service: /service backend"},
	{"logs", "Log một service: /logs backend 300 30m từ_khoá"},
	{"errors", "Chỉ dòng lỗi: /errors backend 1h"},
	{"top", "API gọi nhiều, chậm, lỗi nhiều nhất: /top 1h"},
	{"db", "Postgres, connection pool, Redis, cache"},
	{"alerts", "Alert đang kêu"},
	{"backups", "Danh sách và tình trạng backup"},
	{"disk", "Dung lượng ổ đĩa và Docker"},
	{"report", "Báo cáo tổng hợp 24 giờ"},
	{"backup", "⚠️ Chạy backup DB ngay"},
	{"restart", "⚠️ Restart một service: /restart backend"},
	{"rollback", "⚠️ Đưa backend về image trước"},
	{"prune", "⚠️ Dọn image và build cache cũ"},
	{"cancel", "Huỷ thao tác đang chờ xác nhận"},
	{"help", "Danh sách lệnh"},
}

func NewBot(cfg Config) *Bot {
	httpc := &http.Client{Timeout: 15 * time.Second}
	b := &Bot{
		cfg: cfg, tg: NewTG(cfg.Token), docker: Docker{cfg.Project},
		prom: Prom{cfg.PromURL, httpc}, httpc: httpc, totp: NewTOTP(cfg.TOTPSecret),
		audit: &Audit{path: cfg.AuditLog}, pending: map[int64]*Action{},
	}
	b.readers = map[string]func(context.Context, []string) (Reply, error){
		"status": b.cmdStatus, "services": b.cmdServices, "service": b.cmdService, "logs": b.cmdLogs,
		"errors": b.cmdErrors, "top": b.cmdTop, "db": b.cmdDB, "alerts": b.cmdAlerts,
		"backups": b.cmdBackups, "disk": b.cmdDisk, "report": b.cmdReport, "help": b.cmdHelp, "start": b.cmdHelp,
	}
	b.actions = map[string]func(context.Context, []string) (*Action, error){
		"restart": b.actRestart, "backup": b.actBackup, "rollback": b.actRollback, "prune": b.actPrune,
	}
	return b
}

func (b *Bot) Run(ctx context.Context) {
	if err := b.tg.SetCommands(ctx, commandHelp); err != nil {
		log.Printf("setMyCommands: %v", err)
	}
	b.notify(ctx, "🤖 <b>Bot vận hành đã khởi động.</b> Gõ /help để xem lệnh.")
	go b.watchdog(ctx)
	go b.dailyReport(ctx)
	var offset int64
	for ctx.Err() == nil {
		updates, err := b.tg.Updates(ctx, offset)
		if err != nil {
			log.Printf("getUpdates: %v", err)
			sleep(ctx, 5*time.Second)
			continue
		}
		for _, u := range updates {
			offset = u.UpdateID + 1
			go b.handle(ctx, u)
		}
	}
}

func (b *Bot) notify(ctx context.Context, html string) {
	if b.cfg.NotifyChatID == 0 {
		return
	}
	if err := b.tg.Send(ctx, b.cfg.NotifyChatID, html, nil); err != nil {
		log.Printf("notify: %v", err)
	}
}

func (b *Bot) handle(ctx context.Context, u Update) {
	defer func() {
		if r := recover(); r != nil {
			log.Printf("panic handling update %d: %v", u.UpdateID, r)
		}
	}()
	if cb := u.CallbackQuery; cb != nil {
		b.handleCallback(ctx, cb)
		return
	}
	m := u.Message
	if m == nil || m.From == nil {
		return
	}
	if !b.cfg.AllowedIDs[m.From.ID] {
		b.audit.Record(m.From.ID, clip(m.Text, 80), "denied")
		return // say nothing: an unknown user learns nothing about the server
	}
	text := strings.TrimSpace(m.Text)
	if sixDigits.MatchString(text) {
		b.confirmWithCode(ctx, m.From.ID, m.Chat.ID, text)
		return
	}
	if !strings.HasPrefix(text, "/") {
		_ = b.tg.Send(ctx, m.Chat.ID, "Gõ /help để xem lệnh.", nil)
		return
	}
	fields := strings.Fields(text)
	name := strings.ToLower(strings.TrimPrefix(strings.SplitN(fields[0], "@", 2)[0], "/"))
	args := fields[1:]
	b.audit.Record(m.From.ID, clip(text, 200), "received")

	if name == "cancel" {
		b.mu.Lock()
		_, had := b.pending[m.From.ID]
		delete(b.pending, m.From.ID)
		b.mu.Unlock()
		_ = b.tg.Send(ctx, m.Chat.ID, map[bool]string{true: "Đã huỷ thao tác đang chờ.", false: "Không có thao tác nào đang chờ."}[had], nil)
		return
	}
	if read, ok := b.readers[name]; ok {
		cctx, cancel := context.WithTimeout(ctx, 2*time.Minute)
		defer cancel()
		reply, err := read(cctx, args)
		b.deliver(ctx, m.Chat.ID, reply, err)
		return
	}
	if act, ok := b.actions[name]; ok {
		a, err := act(ctx, args)
		if err != nil {
			_ = b.tg.Send(ctx, m.Chat.ID, "❌ "+esc(err.Error()), nil)
			return
		}
		b.ask(ctx, m.From.ID, m.Chat.ID, a)
		return
	}
	_ = b.tg.Send(ctx, m.Chat.ID, "Không có lệnh /"+esc(name)+". Gõ /help.", nil)
}

var sixDigits = regexp.MustCompile(`^\d{6}$`)

func (b *Bot) deliver(ctx context.Context, chat int64, r Reply, err error) {
	if err != nil {
		_ = b.tg.Send(ctx, chat, "❌ "+esc(err.Error()), nil)
		return
	}
	if r.Doc != nil {
		if e := b.tg.SendDocument(ctx, chat, r.DocName, r.Doc, clip(r.HTML, 900)); e != nil {
			_ = b.tg.Send(ctx, chat, "❌ gửi file lỗi: "+esc(e.Error()), nil)
		}
		return
	}
	if e := b.tg.Send(ctx, chat, r.HTML, nil); e != nil {
		log.Printf("send: %v", e)
	}
}

func newID() string {
	var raw [6]byte
	_, _ = rand.Read(raw[:])
	return hex.EncodeToString(raw[:])
}

func (b *Bot) ask(ctx context.Context, user, chat int64, a *Action) {
	a.ID, a.Chat, a.Expires = newID(), chat, time.Now().Add(2*time.Minute)
	b.mu.Lock()
	b.pending[user] = a
	b.mu.Unlock()
	if b.totp != nil {
		_ = b.tg.Send(ctx, chat, "⚠️ <b>"+esc(a.Label)+"</b>\nGửi mã 6 số từ app Authenticator trong 2 phút để thực hiện.",
			[][]Button{{{Text: "❌ Huỷ", Data: "no:" + a.ID}}})
		return
	}
	_ = b.tg.Send(ctx, chat, "⚠️ <b>"+esc(a.Label)+"</b>\nXác nhận trong 2 phút?",
		[][]Button{{{Text: "✅ Thực hiện", Data: "ok:" + a.ID}, {Text: "❌ Huỷ", Data: "no:" + a.ID}}})
}

func (b *Bot) take(user int64, id string) *Action {
	b.mu.Lock()
	defer b.mu.Unlock()
	a := b.pending[user]
	if a == nil || (id != "" && a.ID != id) {
		return nil
	}
	delete(b.pending, user)
	if time.Now().After(a.Expires) {
		return nil
	}
	return a
}

func (b *Bot) handleCallback(ctx context.Context, cb *Callback) {
	if !b.cfg.AllowedIDs[cb.From.ID] {
		b.audit.Record(cb.From.ID, "callback "+cb.Data, "denied")
		b.tg.AnswerCallback(ctx, cb.ID, "")
		return
	}
	verb, id, _ := strings.Cut(cb.Data, ":")
	if verb == "no" {
		b.take(cb.From.ID, id)
		b.tg.AnswerCallback(ctx, cb.ID, "Đã huỷ")
		if cb.Message != nil {
			b.tg.EditText(ctx, cb.Message.Chat.ID, cb.Message.MessageID, "❌ Đã huỷ.")
		}
		return
	}
	if verb != "ok" || b.totp != nil {
		b.tg.AnswerCallback(ctx, cb.ID, "Cần mã TOTP")
		return
	}
	a := b.take(cb.From.ID, id)
	b.tg.AnswerCallback(ctx, cb.ID, "")
	if a == nil {
		_ = b.tg.Send(ctx, cb.Message.Chat.ID, "Thao tác đã hết hạn hoặc đã chạy. Gõ lại lệnh.", nil)
		return
	}
	if cb.Message != nil {
		b.tg.EditText(ctx, cb.Message.Chat.ID, cb.Message.MessageID, "⏳ "+esc(a.Label)+"…")
	}
	b.execute(ctx, cb.From.ID, a)
}

func (b *Bot) confirmWithCode(ctx context.Context, user, chat int64, code string) {
	if b.totp == nil {
		_ = b.tg.Send(ctx, chat, "Bot chưa bật TOTP: bấm nút ✅ dưới thao tác.", nil)
		return
	}
	b.mu.Lock()
	a := b.pending[user]
	b.mu.Unlock()
	if a == nil {
		_ = b.tg.Send(ctx, chat, "Không có thao tác nào đang chờ mã.", nil)
		return
	}
	if err := b.totp.Verify(code, time.Now()); err != nil {
		b.audit.Record(user, "totp "+a.Label, "rejected: "+err.Error())
		_ = b.tg.Send(ctx, chat, "❌ "+esc(err.Error()), nil)
		return
	}
	if a = b.take(user, a.ID); a == nil {
		_ = b.tg.Send(ctx, chat, "Thao tác đã hết hạn. Gõ lại lệnh.", nil)
		return
	}
	b.execute(ctx, user, a)
}

func (b *Bot) execute(ctx context.Context, user int64, a *Action) {
	if !b.acting.TryLock() {
		_ = b.tg.Send(ctx, a.Chat, "⏳ Đang có thao tác khác chạy, thử lại sau.", nil)
		return
	}
	defer b.acting.Unlock()
	_ = b.tg.Send(ctx, a.Chat, "⏳ Đang chạy: "+esc(a.Label)+"…", nil)
	cctx, cancel := context.WithTimeout(ctx, 10*time.Minute)
	defer cancel()
	started := time.Now()
	out, err := a.Run(cctx)
	took := durH(time.Since(started))
	if err != nil {
		b.audit.Record(user, a.Label, "failed: "+clip(err.Error(), 300))
		_ = b.tg.Send(ctx, a.Chat, fmt.Sprintf("❌ <b>%s</b> lỗi sau %s\n%s", esc(a.Label), took, pre(Redact(err.Error()+"\n"+out))), nil)
		return
	}
	b.audit.Record(user, a.Label, "ok")
	_ = b.tg.Send(ctx, a.Chat, fmt.Sprintf("✅ <b>%s</b> xong sau %s\n%s", esc(a.Label), took, out), nil)
}

func sleep(ctx context.Context, d time.Duration) {
	select {
	case <-ctx.Done():
	case <-time.After(d):
	}
}

package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"time"
)

type TG struct {
	base string
	http *http.Client
}

type User struct {
	ID       int64  `json:"id"`
	Username string `json:"username"`
}
type Chat struct {
	ID   int64  `json:"id"`
	Type string `json:"type"`
}
type Message struct {
	MessageID int64  `json:"message_id"`
	From      *User  `json:"from"`
	Chat      Chat   `json:"chat"`
	Text      string `json:"text"`
}
type Callback struct {
	ID      string   `json:"id"`
	From    User     `json:"from"`
	Message *Message `json:"message"`
	Data    string   `json:"data"`
}
type Update struct {
	UpdateID      int64     `json:"update_id"`
	Message       *Message  `json:"message"`
	CallbackQuery *Callback `json:"callback_query"`
}

type Button struct {
	Text string `json:"text"`
	Data string `json:"callback_data"`
}

func NewTG(token string) *TG {
	return &TG{base: "https://api.telegram.org/bot" + token, http: &http.Client{Timeout: 70 * time.Second}}
}

func (t *TG) call(ctx context.Context, method string, body any, out any) error {
	raw, err := json.Marshal(body)
	if err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, t.base+"/"+method, bytes.NewReader(raw))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	return t.do(req, method, out)
}

func (t *TG) do(req *http.Request, method string, out any) error {
	resp, err := t.http.Do(req)
	if err != nil {
		// The URL holds the token; never let it reach logs.
		return fmt.Errorf("telegram %s: request failed", method)
	}
	defer resp.Body.Close()
	var env struct {
		OK          bool            `json:"ok"`
		Description string          `json:"description"`
		Result      json.RawMessage `json:"result"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&env); err != nil {
		return fmt.Errorf("telegram %s: %v", method, err)
	}
	if !env.OK {
		return fmt.Errorf("telegram %s: %s", method, env.Description)
	}
	if out != nil {
		return json.Unmarshal(env.Result, out)
	}
	return nil
}

func (t *TG) Updates(ctx context.Context, offset int64) ([]Update, error) {
	var out []Update
	err := t.call(ctx, "getUpdates", map[string]any{
		"offset": offset, "timeout": 50, "allowed_updates": []string{"message", "callback_query"},
	}, &out)
	return out, err
}

func keyboard(rows [][]Button) map[string]any {
	return map[string]any{"inline_keyboard": rows}
}

// Send posts an HTML message, splitting it under Telegram's 4096-character limit.
func (t *TG) Send(ctx context.Context, chat int64, html string, buttons [][]Button) error {
	for _, part := range splitMessage(html, 3900) {
		body := map[string]any{"chat_id": chat, "text": part, "parse_mode": "HTML", "disable_web_page_preview": true}
		if buttons != nil {
			body["reply_markup"] = keyboard(buttons)
		}
		if err := t.call(ctx, "sendMessage", body, nil); err != nil {
			return err
		}
	}
	return nil
}

func (t *TG) SendDocument(ctx context.Context, chat int64, name string, content []byte, caption string) error {
	var buf bytes.Buffer
	w := multipart.NewWriter(&buf)
	_ = w.WriteField("chat_id", fmt.Sprint(chat))
	_ = w.WriteField("caption", caption)
	_ = w.WriteField("parse_mode", "HTML")
	part, err := w.CreateFormFile("document", name)
	if err != nil {
		return err
	}
	if _, err := io.Copy(part, bytes.NewReader(content)); err != nil {
		return err
	}
	if err := w.Close(); err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, t.base+"/sendDocument", &buf)
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", w.FormDataContentType())
	return t.do(req, "sendDocument", nil)
}

func (t *TG) AnswerCallback(ctx context.Context, id, text string) {
	_ = t.call(ctx, "answerCallbackQuery", map[string]any{"callback_query_id": id, "text": text}, nil)
}

func (t *TG) EditText(ctx context.Context, chat, messageID int64, html string) {
	_ = t.call(ctx, "editMessageText", map[string]any{
		"chat_id": chat, "message_id": messageID, "text": html, "parse_mode": "HTML",
	}, nil)
}

func (t *TG) SetCommands(ctx context.Context, cmds [][2]string) error {
	list := make([]map[string]string, 0, len(cmds))
	for _, c := range cmds {
		list = append(list, map[string]string{"command": c[0], "description": c[1]})
	}
	return t.call(ctx, "setMyCommands", map[string]any{"commands": list}, nil)
}

// splitMessage cuts on line boundaries and keeps an open <pre> block balanced across parts.
func splitMessage(s string, limit int) []string {
	if len(s) <= limit {
		return []string{s}
	}
	var parts []string
	var cur bytes.Buffer
	inPre := false
	for _, line := range bytesLines(s) {
		if cur.Len()+len(line)+1 > limit && cur.Len() > 0 {
			if inPre {
				cur.WriteString("</pre>")
			}
			parts = append(parts, cur.String())
			cur.Reset()
			if inPre {
				cur.WriteString("<pre>")
			}
		}
		if len(line) > limit {
			line = line[:limit-20] + "…"
		}
		cur.WriteString(line)
		cur.WriteByte('\n')
		if bytes.Count([]byte(line), []byte("<pre>")) > bytes.Count([]byte(line), []byte("</pre>")) {
			inPre = true
		} else if bytes.Count([]byte(line), []byte("</pre>")) > bytes.Count([]byte(line), []byte("<pre>")) {
			inPre = false
		}
	}
	if cur.Len() > 0 {
		parts = append(parts, cur.String())
	}
	return parts
}

func bytesLines(s string) []string {
	var out []string
	start := 0
	for i := 0; i < len(s); i++ {
		if s[i] == '\n' {
			out = append(out, s[start:i])
			start = i + 1
		}
	}
	if start < len(s) {
		out = append(out, s[start:])
	}
	return out
}

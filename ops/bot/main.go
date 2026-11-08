// Command erg-ops-bot is a Telegram bot that monitors and operates the ERG production server.
//
// `erg-ops-bot run status` prints one read-only command's reply to stdout instead of starting the bot.
package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"
)

func main() {
	cfg, err := LoadConfig()
	if err != nil {
		log.Fatalf("config: %v", err)
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	b := NewBot(cfg)
	if len(os.Args) > 2 && os.Args[1] == "run" {
		read, ok := b.readers[os.Args[2]]
		if !ok {
			log.Fatalf("no read-only command %q", os.Args[2])
		}
		cctx, cancel := context.WithTimeout(ctx, 2*time.Minute)
		defer cancel()
		r, err := read(cctx, os.Args[3:])
		if err != nil {
			log.Fatal(err)
		}
		fmt.Println(r.HTML)
		if r.Doc != nil {
			fmt.Printf("[file %s, %d bytes]\n", r.DocName, len(r.Doc))
		}
		return
	}
	b.Run(ctx)
}

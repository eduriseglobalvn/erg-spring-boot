package com.erg.ergspringboot.platform.webhook;

import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebhookReplayGuard {
    private final JdbcTemplate jdbc;

    public WebhookReplayGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void claim(String provider, String receiptKey, Duration ttl) {
        int changed = jdbc.update("""
                insert into webhook_receipts(provider, receipt_key, received_at, expires_at)
                values (?, ?, current_timestamp, current_timestamp + (? * interval '1 millisecond'))
                on conflict (provider, receipt_key) do update
                  set received_at = excluded.received_at, expires_at = excluded.expires_at
                  where webhook_receipts.expires_at < current_timestamp
                """, provider, receiptKey, ttl.toMillis());
        if (changed == 0) throw new WebhookReplayException(provider);
    }
}

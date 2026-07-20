package com.erg.ergspringboot.platform.webhook;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookInboxService {
    private final WebhookReplayGuard replayGuard;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final WebhookProperties properties;
    private final MeterRegistry metrics;

    public WebhookInboxService(WebhookReplayGuard replayGuard, JdbcTemplate jdbc, ObjectMapper mapper,
            WebhookProperties properties, ObjectProvider<MeterRegistry> metrics) {
        this.replayGuard = replayGuard;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.properties = properties;
        this.metrics = metrics.getIfAvailable();
    }

    @Transactional("postgresTransactionManager")
    public void accept(String provider, String receiptKey, JsonNode payload) {
        replayGuard.claim(provider, receiptKey, properties.getReplayTtl());
        jdbc.update("""
                insert into webhook_inbox(provider, receipt_key, payload, status, received_at)
                values (?, ?, cast(? as jsonb), 'pending', current_timestamp)
                """, provider, receiptKey, json(payload));
        counter("webhook.accepted", provider);
    }

    public void rejected(String provider, String reason) { counter("webhook.rejected." + reason, provider); }

    private String json(JsonNode payload) {
        try { return mapper.writeValueAsString(payload); }
        catch (Exception exception) { throw new IllegalArgumentException("Webhook payload cannot be serialized", exception); }
    }

    private void counter(String name, String provider) {
        if (metrics != null) metrics.counter(name, "provider", provider).increment();
    }
}

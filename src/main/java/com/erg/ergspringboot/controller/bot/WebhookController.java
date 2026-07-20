package com.erg.ergspringboot.controller.bot;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.platform.webhook.DiscordEd25519SignatureVerifier;
import com.erg.ergspringboot.platform.webhook.DiscordWebhookPayload;
import com.erg.ergspringboot.platform.webhook.TelegramSecretTokenVerifier;
import com.erg.ergspringboot.platform.webhook.TelegramWebhookPayload;
import com.erg.ergspringboot.platform.webhook.WebhookInboxService;
import com.erg.ergspringboot.platform.webhook.WebhookProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookController {
    private final DiscordEd25519SignatureVerifier discordVerifier;
    private final TelegramSecretTokenVerifier telegramVerifier;
    private final WebhookInboxService inbox;
    private final WebhookProperties properties;
    private final ObjectMapper mapper;

    public WebhookController(DiscordEd25519SignatureVerifier discordVerifier,
            TelegramSecretTokenVerifier telegramVerifier, WebhookInboxService inbox,
            WebhookProperties properties, ObjectMapper mapper) {
        this.discordVerifier = discordVerifier;
        this.telegramVerifier = telegramVerifier;
        this.inbox = inbox;
        this.properties = properties;
        this.mapper = mapper;
    }

    @PostMapping(path = "/webhooks/discord", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> discord(@RequestBody byte[] rawBody,
            @RequestHeader("X-Signature-Ed25519") String signature,
            @RequestHeader("X-Signature-Timestamp") String timestamp,
            HttpServletRequest request) {
        enforceBodyLimit(rawBody);
        discordVerifier.verify(rawBody, signature, timestamp);
        DiscordWebhookPayload payload = read(rawBody, DiscordWebhookPayload.class);
        JsonNode json = readTree(rawBody);
        String receiptKey = payload.id() == null || payload.id().isBlank() ? signature : payload.id();
        inbox.accept("discord", receiptKey, json);
        return accepted(request);
    }

    @PostMapping(path = "/webhooks/telegram", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> telegram(@RequestBody byte[] rawBody,
            @RequestHeader("X-Telegram-Bot-Api-Secret-Token") String secretToken,
            HttpServletRequest request) {
        enforceBodyLimit(rawBody);
        telegramVerifier.verify(secretToken);
        TelegramWebhookPayload payload = read(rawBody, TelegramWebhookPayload.class);
        JsonNode json = readTree(rawBody);
        inbox.accept("telegram", Long.toString(payload.updateId()), json);
        return accepted(request);
    }

    private void enforceBodyLimit(byte[] body) {
        if (body.length == 0 || body.length > properties.getMaxBodyBytes()) {
            throw new BadRequestException("Webhook body exceeds the allowed size");
        }
    }

    private <T> T read(byte[] body, Class<T> type) {
        try { return mapper.readValue(body, type); }
        catch (Exception exception) { throw new BadRequestException("Webhook body is invalid"); }
    }

    private JsonNode readTree(byte[] body) {
        try { return mapper.readTree(body); }
        catch (Exception exception) { throw new BadRequestException("Webhook body is invalid"); }
    }

    private ResponseEntity<ApiResponse<Void>> accepted(HttpServletRequest request) {
        String path = request.getRequestURI();
        Object requestId = request.getAttribute("requestId");
        String id = requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
        return ResponseEntity.accepted().body(ApiResponse.success(202, "Webhook accepted", null, path, id));
    }
}

package com.erg.ergspringboot.platform.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class TelegramSecretTokenVerifier {
    private final WebhookProperties properties;

    public TelegramSecretTokenVerifier(WebhookProperties properties) { this.properties = properties; }

    public void verify(String suppliedToken) {
        String expected = properties.getTelegramSecretToken();
        if (expected == null || expected.isBlank() || suppliedToken == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new WebhookVerificationException("Telegram webhook secret token is invalid");
        }
    }
}

package com.erg.ergspringboot.platform.webhook;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class WebhookSecurityTest {
    private static final Instant NOW = Instant.parse("2026-07-20T05:00:00Z");

    @Test
    void discordVerifierAcceptsRawBodySignedWithFreshTimestamp() throws Exception {
        byte[] body = "{\"id\":\"evt-1\",\"type\":1}".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(NOW.getEpochSecond());
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        WebhookProperties properties = properties();
        byte[] encoded = keyPair.getPublic().getEncoded();
        properties.setDiscordPublicKey(java.util.HexFormat.of().formatHex(
                java.util.Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length)));
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(timestamp.getBytes(StandardCharsets.UTF_8));
        signer.update(body);
        String signature = java.util.HexFormat.of().formatHex(signer.sign());

        new DiscordEd25519SignatureVerifier(properties, Clock.fixed(NOW, ZoneOffset.UTC))
                .verify(body, signature, timestamp);
    }

    @Test
    void discordVerifierRejectsStaleTimestampBeforeSignatureWork() {
        WebhookProperties properties = properties();
        properties.setDiscordPublicKey("00".repeat(32));

        assertThatThrownBy(() -> new DiscordEd25519SignatureVerifier(
                properties, Clock.fixed(NOW, ZoneOffset.UTC)).verify(
                "{}".getBytes(StandardCharsets.UTF_8), "00".repeat(64),
                Long.toString(NOW.minus(Duration.ofMinutes(6)).getEpochSecond())))
                .isInstanceOf(WebhookVerificationException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    void telegramTokenComparisonFailsClosed() {
        WebhookProperties properties = properties();
        properties.setTelegramSecretToken("expected-secret");
        TelegramSecretTokenVerifier verifier = new TelegramSecretTokenVerifier(properties);

        assertThatThrownBy(() -> verifier.verify("wrong-secret"))
                .isInstanceOf(WebhookVerificationException.class);
    }

    @Test
    void replayGuardRejectsAnExistingReceipt() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbc.update(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("telegram"),
                org.mockito.ArgumentMatchers.eq("42"),
                org.mockito.ArgumentMatchers.eq(86_400_000L))).thenReturn(0);

        assertThatThrownBy(() -> new WebhookReplayGuard(jdbc)
                .claim("telegram", "42", Duration.ofHours(24)))
                .isInstanceOf(WebhookReplayException.class);

        verify(jdbc).update(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("telegram"),
                org.mockito.ArgumentMatchers.eq("42"),
                org.mockito.ArgumentMatchers.eq(86_400_000L));
    }

    private WebhookProperties properties() {
        WebhookProperties properties = new WebhookProperties();
        properties.setTimestampTolerance(Duration.ofMinutes(5));
        return properties;
    }
}

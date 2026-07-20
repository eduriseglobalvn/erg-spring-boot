package com.erg.ergspringboot.platform.webhook;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public final class DiscordEd25519SignatureVerifier implements WebhookSignatureVerifier {
    private static final byte[] X509_ED25519_PREFIX = java.util.HexFormat.of().parseHex("302a300506032b6570032100");
    private final WebhookProperties properties;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public DiscordEd25519SignatureVerifier(WebhookProperties properties) {
        this(properties, Clock.systemUTC());
    }

    DiscordEd25519SignatureVerifier(WebhookProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void verify(byte[] rawBody, String signatureHeader, String timestampHeader) {
        try {
            if (blank(properties.getDiscordPublicKey()) || blank(signatureHeader) || blank(timestampHeader)) {
                throw new WebhookVerificationException("Discord webhook signature is missing");
            }
            Instant sentAt = Instant.ofEpochSecond(Long.parseLong(timestampHeader));
            if (sentAt.isBefore(clock.instant().minus(properties.getTimestampTolerance()))
                    || sentAt.isAfter(clock.instant().plus(properties.getTimestampTolerance()))) {
                throw new WebhookVerificationException("Discord webhook timestamp is outside the accepted window");
            }
            byte[] rawKey = java.util.HexFormat.of().parseHex(properties.getDiscordPublicKey().trim());
            if (rawKey.length != 32) throw new WebhookVerificationException("Discord public key is invalid");
            byte[] encodedKey = new byte[X509_ED25519_PREFIX.length + rawKey.length];
            System.arraycopy(X509_ED25519_PREFIX, 0, encodedKey, 0, X509_ED25519_PREFIX.length);
            System.arraycopy(rawKey, 0, encodedKey, X509_ED25519_PREFIX.length, rawKey.length);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encodedKey)));
            verifier.update(timestampHeader.getBytes(StandardCharsets.UTF_8));
            verifier.update(rawBody);
            if (!verifier.verify(java.util.HexFormat.of().parseHex(signatureHeader.trim()))) {
                throw new WebhookVerificationException("Discord webhook signature is invalid");
            }
        } catch (WebhookVerificationException exception) {
            throw exception;
        } catch (RuntimeException | java.security.GeneralSecurityException exception) {
            throw new WebhookVerificationException("Discord webhook signature is invalid");
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}

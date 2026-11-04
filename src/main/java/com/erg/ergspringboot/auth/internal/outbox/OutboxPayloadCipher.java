package com.erg.ergspringboot.auth.internal.outbox;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Encrypts one-time credentials/codes before they are persisted in JSONB. */
@Component
public class OutboxPayloadCipher {
    private static final int TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    /** Source-visible fallback; acceptable only outside the {@code prod} profile. */
    static final String DEVELOPMENT_KEY = "development-only-change-me";

    @Autowired
    public OutboxPayloadCipher(
            @Value("${ERG_OUTBOX_ENCRYPTION_KEY:${SECRET_OUTBOX__KEY:}}") String configuredKey,
            Environment environment
    ) {
        this(requireProductionKey(configuredKey, environment));
    }

    public OutboxPayloadCipher(String configuredKey) {
        String effectiveKey = configuredKey == null || configuredKey.isBlank() ? DEVELOPMENT_KEY : configuredKey;
        try {
            this.key = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(effectiveKey.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot initialize outbox payload cipher", exception);
        }
    }

    private static String requireProductionKey(String configuredKey, Environment environment) {
        boolean production = environment != null && environment.matchesProfiles("prod");
        if (production && (configuredKey == null || configuredKey.isBlank() || DEVELOPMENT_KEY.equals(configuredKey.trim()))) {
            throw new IllegalStateException(
                    "ERG_OUTBOX_ENCRYPTION_KEY must be set to a non-default secret in the prod profile");
        }
        return configuredKey;
    }

    public String encrypt(String value) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce).put(encrypted).array());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot encrypt outbox payload", exception);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] packed = Base64.getDecoder().decode(encoded);
            byte[] nonce = java.util.Arrays.copyOfRange(packed, 0, NONCE_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(packed, NONCE_BYTES, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot decrypt outbox payload", exception);
        }
    }
}

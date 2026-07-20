package com.erg.ergspringboot.service.notifications;

import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class NotificationTokenCipher {

    private static final String PREFIX = "enc:v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec secretKey;
    private final boolean encryptionRequired;
    private final SecureRandom secureRandom = new SecureRandom();

    public NotificationTokenCipher(NotificationPushProperties properties) {
        this.secretKey = key(trimToNull(properties.getCredentials().getRegistrationTokenEncryptionKey()));
        this.encryptionRequired = properties.getCredentials().isRegistrationTokenEncryptionRequired();
        if (encryptionRequired && secretKey == null) {
            throw new IllegalStateException("Notification registration token encryption is required but no key is configured.");
        }
    }

    public String encrypt(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (secretKey == null || token.startsWith(PREFIX)) {
            return token;
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(nonce.length + ciphertext.length);
            buffer.put(nonce);
            buffer.put(ciphertext);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt notification registration token.", exception);
        }
    }

    public String decrypt(String storedToken) {
        if (storedToken == null || storedToken.isBlank()) {
            return null;
        }
        if (!storedToken.startsWith(PREFIX)) {
            if (encryptionRequired) {
                throw new IllegalStateException("Notification registration token is not encrypted.");
            }
            return storedToken;
        }
        if (secretKey == null) {
            throw new IllegalStateException("Notification registration token is encrypted but no key is configured.");
        }
        try {
            byte[] encoded = Base64.getUrlDecoder().decode(storedToken.substring(PREFIX.length()));
            if (encoded.length <= NONCE_BYTES) {
                throw new IllegalStateException("Encrypted notification registration token is malformed.");
            }
            byte[] nonce = Arrays.copyOfRange(encoded, 0, NONCE_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(encoded, NONCE_BYTES, encoded.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to decrypt notification registration token.", exception);
        }
    }

    private SecretKeySpec key(String configuredKey) {
        if (configuredKey == null) {
            return null;
        }
        byte[] raw = decodeKey(configuredKey);
        if (raw.length == 16 || raw.length == 24 || raw.length == 32) {
            return new SecretKeySpec(raw, "AES");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new SecretKeySpec(digest.digest(configuredKey.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive notification token encryption key.", exception);
        }
    }

    private byte[] decodeKey(String configuredKey) {
        try {
            return Base64.getDecoder().decode(configuredKey);
        } catch (IllegalArgumentException ignored) {
            return configuredKey.getBytes(StandardCharsets.UTF_8);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

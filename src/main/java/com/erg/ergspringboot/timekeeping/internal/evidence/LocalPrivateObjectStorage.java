package com.erg.ergspringboot.timekeeping.internal.evidence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev/default punch-photo store on local disk. URLs point at {@link #PHOTO_FILE_PATH} and carry an
 * HMAC-SHA256 over key and expiry; the serving controller must call {@link #verify} before reading.
 */
public final class LocalPrivateObjectStorage implements PrivateObjectStorage {

    /** The one place the local photo-serving path is spelled; the controller maps this path. */
    public static final String PHOTO_FILE_PATH = "/api/v1/timekeeping/photos/file";
    static final String KEY_PREFIX = "private/timekeeping/";

    private static final Logger log = LoggerFactory.getLogger(LocalPrivateObjectStorage.class);

    private final Path root;
    private final byte[] secret;
    private final Clock clock;

    LocalPrivateObjectStorage(Path root, String signingSecret, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        this.clock = clock;
        if (signingSecret == null || signingSecret.isBlank()) {
            log.warn("erg.timekeeping.photos.signing-secret is not set: using a random per-process secret, "
                    + "photo URLs will not survive a restart or work across instances");
            this.secret = new byte[32];
            new SecureRandom().nextBytes(this.secret);
        } else if (signingSecret.length() < 32) {
            throw new IllegalArgumentException("timekeeping photo signing secret must contain at least 32 characters");
        } else {
            this.secret = signingSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temporary, bytes);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot store punch photo", exception);
        }
    }

    @Override
    public URI presignGet(String key, Duration ttl) {
        resolve(key);
        long exp = clock.instant().plus(ttl).getEpochSecond();
        return URI.create(PHOTO_FILE_PATH + "?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "&exp=" + exp + "&sig=" + sign(key, exp));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot delete punch photo", exception);
        }
    }

    /** True only for an unexpired URL signed by this store for exactly this key and expiry. */
    public boolean verify(String key, long exp, String sig) {
        if (key == null || sig == null || clock.instant().getEpochSecond() > exp) return false;
        try {
            resolve(key);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        return MessageDigest.isEqual(sign(key, exp).getBytes(StandardCharsets.US_ASCII),
                sig.getBytes(StandardCharsets.US_ASCII));
    }

    private Path resolve(String key) {
        if (key == null || !key.startsWith(KEY_PREFIX) || key.contains("..") || key.contains("\\")
                || key.contains(":") || key.startsWith("/")) {
            throw new IllegalArgumentException("invalid punch photo key");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) throw new IllegalArgumentException("invalid punch photo key");
        return resolved;
    }

    private String sign(String key, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((key + "\n" + exp).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 unavailable", exception);
        }
    }
}

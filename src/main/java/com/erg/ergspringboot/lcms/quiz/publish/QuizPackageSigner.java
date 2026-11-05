package com.erg.ergspringboot.lcms.quiz.publish;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Content hashing and signing for immutable quiz packages.
 *
 * <p>New desktop releases should use Ed25519 with a PKCS#8 private key from
 * secret configuration. HMAC-SHA256 remains an explicit compatibility mode
 * for existing web/runtime rows; it is never marked portable-ready because a
 * client must not receive an HMAC secret.
 *
 * <p>HMAC keys form a key ring for rotation: {@code erg.quiz.package.signing-keys.<kid>=secret} lists every
 * key still accepted for verification and {@code erg.quiz.package.active-key-id} picks the one that signs.
 * The legacy single {@code signing-key} stays valid: it is registered under {@code public-key-id}, which is
 * also the default active kid, so existing configuration keeps working unchanged.
 */
@Component
public class QuizPackageSigner {

    private final Map<String, String> hmacKeys;
    private final String publicKeyId;
    private final String algorithm;
    private final PrivateKey ed25519PrivateKey;

    @Autowired
    public QuizPackageSigner(
            @Value("${erg.quiz.package.signing-key:}") String signingKey,
            @Value("${erg.quiz.package.public-key-id:unset}") String publicKeyId,
            @Value("${erg.quiz.package.signing-algorithm:HMAC-SHA256}") String algorithm,
            @Value("${erg.quiz.package.ed25519-private-key:}") String ed25519PrivateKey,
            @Value("${erg.quiz.package.active-key-id:}") String activeKeyId,
            Environment environment
    ) {
        this(signingKey, publicKeyId, algorithm, ed25519PrivateKey, activeKeyId,
                Binder.get(environment).bind("erg.quiz.package.signing-keys", Bindable.mapOf(String.class, String.class))
                        .orElseGet(Map::of));
    }

    QuizPackageSigner(String signingKey, String publicKeyId, String algorithm, String ed25519PrivateKey,
                      String activeKeyId, Map<String, String> ringKeys) {
        Map<String, String> ring = new LinkedHashMap<>();
        if (signingKey != null && !signingKey.isEmpty()) {
            ring.put(publicKeyId, signingKey);
        }
        ring.putAll(ringKeys);
        this.hmacKeys = Map.copyOf(ring);
        this.publicKeyId = activeKeyId == null || activeKeyId.isBlank() ? publicKeyId : activeKeyId;
        this.algorithm = normalizeAlgorithm(algorithm);
        this.ed25519PrivateKey = loadEd25519Key(this.algorithm, ed25519PrivateKey);
    }

    /** Compatibility constructor used by tests and legacy web publishing. */
    public QuizPackageSigner(String signingKey, String publicKeyId) {
        this(signingKey, publicKeyId, "HMAC-SHA256", "", "", Map.of());
    }

    public QuizPackageSigner(String signingKey, String publicKeyId, String algorithm, String ed25519PrivateKey) {
        this(signingKey, publicKeyId, algorithm, ed25519PrivateKey, "", Map.of());
    }

    public String publicKeyId() {
        return publicKeyId;
    }

    public String signatureAlgorithm() {
        return algorithm;
    }

    public String contentHash(String canonicalPayload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + hexEncode(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to hash package payload", ex);
        }
    }

    /** True when {@code signature} is the signature of the payload under {@code keyId}; unknown kids are rejected. */
    public boolean verify(String canonicalPayload, String signature, String keyId) {
        boolean known = "Ed25519".equals(algorithm) ? publicKeyId.equals(keyId) : keyId != null && hmacKeys.containsKey(keyId);
        if (!known || signature == null) {
            return false;
        }
        return MessageDigest.isEqual(
                signWith(canonicalPayload, keyId).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    public String sign(String canonicalPayload) {
        return signWith(canonicalPayload, publicKeyId);
    }

    private String signWith(String canonicalPayload, String keyId) {
        if ("Ed25519".equals(algorithm)) {
            if (ed25519PrivateKey == null) {
                throw new IllegalStateException("Ed25519 package signing key is not configured");
            }
            try {
                Signature signature = Signature.getInstance("Ed25519");
                signature.initSign(ed25519PrivateKey);
                signature.update(canonicalPayload.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(signature.sign());
            } catch (Exception ex) {
                throw new IllegalStateException("failed to sign package payload with Ed25519", ex);
            }
        }
        String secret = hmacKeys.get(keyId);
        if (secret == null) {
            throw new IllegalStateException("quiz package signing key is not configured for key id: " + keyId);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to sign package payload", ex);
        }
    }

    private String normalizeAlgorithm(String value) {
        String normalized = value == null ? "HMAC-SHA256" : value.trim();
        if (normalized.equalsIgnoreCase("Ed25519")) return "Ed25519";
        if (normalized.equalsIgnoreCase("HMAC-SHA256")) return "HMAC-SHA256";
        throw new IllegalArgumentException("Unsupported quiz package signing algorithm: " + value);
    }

    private PrivateKey loadEd25519Key(String selectedAlgorithm, String encoded) {
        if (!"Ed25519".equals(selectedAlgorithm)) return null;
        if (encoded == null || encoded.isBlank()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded.trim());
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid PKCS#8 Ed25519 package signing key", ex);
        }
    }

    private String hexEncode(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }
}

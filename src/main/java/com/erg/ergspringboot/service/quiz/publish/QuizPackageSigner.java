package com.erg.ergspringboot.service.quiz.publish;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
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
 */
@Component
public class QuizPackageSigner {

    private final String signingKey;
    private final String publicKeyId;
    private final String algorithm;
    private final PrivateKey ed25519PrivateKey;

    @Autowired
    public QuizPackageSigner(
            @Value("${erg.quiz.package.signing-key:}") String signingKey,
            @Value("${erg.quiz.package.public-key-id:unset}") String publicKeyId,
            @Value("${erg.quiz.package.signing-algorithm:HMAC-SHA256}") String algorithm,
            @Value("${erg.quiz.package.ed25519-private-key:}") String ed25519PrivateKey
    ) {
        this.signingKey = signingKey;
        this.publicKeyId = publicKeyId;
        this.algorithm = normalizeAlgorithm(algorithm);
        this.ed25519PrivateKey = loadEd25519Key(this.algorithm, ed25519PrivateKey);
    }

    /** Compatibility constructor used by tests and legacy web publishing. */
    public QuizPackageSigner(String signingKey, String publicKeyId) {
        this(signingKey, publicKeyId, "HMAC-SHA256", "");
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

    public String sign(String canonicalPayload) {
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
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
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

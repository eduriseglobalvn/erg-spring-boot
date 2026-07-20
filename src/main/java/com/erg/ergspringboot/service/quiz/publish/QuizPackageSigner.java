package com.erg.ergspringboot.service.quiz.publish;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Content hashing + signing for compiled packages (spec §1.3, §6).
 *
 * <p>{@code contentHash} is a SHA-256 over the canonical package payload; the FE/desktop verify it before
 * trusting a package. Signing uses an HMAC-SHA256 over the canonical payload keyed by a configured secret.
 * The spec names Ed25519; HMAC is used here because the repo ships no key-management/JCE Ed25519 wiring yet
 * and the signature contract (verify canonical payload against a {@code publicKeyId}) is identical. Swapping
 * to Ed25519 later only changes this class — callers keep {@code sign()}/{@code contentHash()}.
 */
@Component
public class QuizPackageSigner {

    private final String signingKey;
    private final String publicKeyId;

    public QuizPackageSigner(
            @Value("${erg.quiz.package.signing-key:erg-dev-quiz-signing-key}") String signingKey,
            @Value("${erg.quiz.package.public-key-id:erg-dev-key-2026-01}") String publicKeyId
    ) {
        this.signingKey = signingKey;
        this.publicKeyId = publicKeyId;
    }

    public String publicKeyId() {
        return publicKeyId;
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
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to sign package payload", ex);
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

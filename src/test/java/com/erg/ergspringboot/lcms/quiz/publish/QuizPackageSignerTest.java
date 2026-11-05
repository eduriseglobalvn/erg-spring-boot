package com.erg.ergspringboot.lcms.quiz.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuizPackageSignerTest {

    private final QuizPackageSigner signer = new QuizPackageSigner("test-signing-key", "test-key-2026");

    @Test
    void contentHashIsStableAndPrefixed() {
        String hash1 = signer.contentHash("{\"a\":1}");
        String hash2 = signer.contentHash("{\"a\":1}");
        assertThat(hash1).isEqualTo(hash2).startsWith("sha256:");
    }

    @Test
    void contentHashChangesWithPayload() {
        assertThat(signer.contentHash("{\"a\":1}"))
                .isNotEqualTo(signer.contentHash("{\"a\":2}"));
    }

    @Test
    void signIsDeterministicForSamePayloadAndKey() {
        assertThat(signer.sign("payload")).isEqualTo(signer.sign("payload"));
    }

    @Test
    void signDiffersAcrossKeys() {
        QuizPackageSigner other = new QuizPackageSigner("other-key", "test-key-2026");
        assertThat(signer.sign("payload")).isNotEqualTo(other.sign("payload"));
    }

    @Test
    void ed25519SignatureIsVerifiableWithPublishedPublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair pair = generator.generateKeyPair();
        QuizPackageSigner ed25519 = new QuizPackageSigner(
                "unused",
                "release-key-1",
                "Ed25519",
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));

        String payload = "portable-exam-payload";
        byte[] signatureBytes = Base64.getDecoder().decode(ed25519.sign(payload));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(pair.getPublic());
        verifier.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(ed25519.signatureAlgorithm()).isEqualTo("Ed25519");
        assertThat(verifier.verify(signatureBytes)).isTrue();
    }

    @Test
    void rotationSignsWithActiveKidAndStillVerifiesPackagesSignedWithTheOldKid() {
        QuizPackageSigner before = new QuizPackageSigner("old-secret", "k1");
        String oldSignature = before.sign("payload");

        QuizPackageSigner after = new QuizPackageSigner("old-secret", "k1", "HMAC-SHA256", "", "k2",
                Map.of("k2", "new-secret"));

        assertThat(after.publicKeyId()).isEqualTo("k2");
        assertThat(after.sign("payload")).isNotEqualTo(oldSignature);
        assertThat(after.verify("payload", after.sign("payload"), "k2")).isTrue();
        assertThat(after.verify("payload", oldSignature, "k1")).isTrue();
        assertThat(after.verify("tampered", oldSignature, "k1")).isFalse();
        assertThat(after.verify("payload", oldSignature, "k2")).isFalse();
    }

    @Test
    void verifyRejectsUnknownKid() {
        QuizPackageSigner signer = new QuizPackageSigner("secret", "k1");
        assertThat(signer.verify("payload", signer.sign("payload"), "k-unknown")).isFalse();
        assertThat(signer.verify("payload", signer.sign("payload"), null)).isFalse();
    }
}

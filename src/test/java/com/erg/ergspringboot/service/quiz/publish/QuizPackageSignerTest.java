package com.erg.ergspringboot.service.quiz.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
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
}

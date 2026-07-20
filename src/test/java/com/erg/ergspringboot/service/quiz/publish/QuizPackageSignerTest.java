package com.erg.ergspringboot.service.quiz.publish;

import static org.assertj.core.api.Assertions.assertThat;

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
}

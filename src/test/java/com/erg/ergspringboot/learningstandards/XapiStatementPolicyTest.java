package com.erg.ergspringboot.learningstandards;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class XapiStatementPolicyTest {
    @Test
    void acceptsBoundedHttpUrisAndHashesActorInsteadOfPersistingRawIdentity() {
        String actor = "learner@example.test";
        String digest = "a".repeat(64);

        var result = XapiStatementPolicy.validate(
                "statement-1", actor, "https://w3id.org/xapi/dod-isd/verbs/attempted",
                "https://example.test/activity/1", digest);

        assertThat(result.valid()).isTrue();
        assertThat(XapiStatementPolicy.actorHash(actor)).hasSize(64).doesNotContain(actor);
    }

    @Test
    void rejectsUnboundedFieldsNonHttpUrisAndMalformedDigest() {
        assertThat(XapiStatementPolicy.validate(
                "statement-1", "actor", "javascript:alert(1)", "https://example.test/object", "a".repeat(64)).valid())
                .isFalse();
        assertThat(XapiStatementPolicy.validate(
                "statement-1", "actor", "https://example.test/verb", "relative/object", "a".repeat(64)).valid())
                .isFalse();
        assertThat(XapiStatementPolicy.validate(
                "statement-1", "actor", "https://example.test/verb", "https://example.test/object", "not-a-digest").valid())
                .isFalse();
        assertThat(XapiStatementPolicy.validate(
                "statement-1", "x".repeat(513), "https://example.test/verb", "https://example.test/object", "a".repeat(64)).valid())
                .isFalse();
    }
}

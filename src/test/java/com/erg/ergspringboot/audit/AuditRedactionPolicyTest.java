package com.erg.ergspringboot.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.audit.internal.AuditRedactionPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditRedactionPolicyTest {
    private final AuditRedactionPolicy policy = new AuditRedactionPolicy();

    @Test
    void redactsSecretsHashesPiiAndKeepsNestedDetailsBounded() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("password", "do-not-store");
        input.put("access_token", "bearer-secret");
        input.put("email", "person@example.com");
        input.put("phone_number", "+84123456789");
        input.put("safe", "value");
        input.put("nested", Map.of("userAgent", "Browser/1.0", "otp", "123456"));

        Map<String, Object> redacted = policy.redact(input);

        assertThat(redacted).containsEntry("password", "[REDACTED]")
                .containsEntry("access_token", "[REDACTED]")
                .containsEntry("safe", "value");
        assertThat(redacted.get("email")).isInstanceOf(String.class)
                .asString().startsWith("hmac-sha256:").doesNotContain("person@example.com");
        assertThat(redacted.get("phone_number")).isInstanceOf(String.class)
                .asString().startsWith("hmac-sha256:");
        assertThat(redacted.get("nested")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) redacted.get("nested");
        assertThat(nested).containsEntry("otp", "[REDACTED]").containsKey("userAgent");
    }

    @Test
    void pseudonymisesWiderPiiKeysWithAKeyedHash() {
        Map<String, Object> input = new LinkedHashMap<>();
        for (String key : List.of("recoveryEmail", "userEmail", "fullName", "name", "dob", "nationalId", "cccd",
                "clientIp", "remoteAddr", "api_key")) {
            input.put(key, "Nguyen Van A 0901234567");
        }
        input.put("fileName", "report.csv");

        Map<String, Object> redacted = new AuditRedactionPolicy("key-one").redact(input);
        Map<String, Object> otherKey = new AuditRedactionPolicy("key-two").redact(input);

        assertThat(AuditRedactionPolicy.VERSION).isEqualTo(2);
        assertThat(redacted.get("api_key")).isEqualTo("[REDACTED]");
        for (String key : List.of("recoveryEmail", "userEmail", "fullName", "name", "dob", "nationalId", "cccd",
                "clientIp", "remoteAddr")) {
            assertThat(redacted.get(key)).as(key).asString().startsWith("hmac-sha256:").doesNotContain("Nguyen");
            assertThat(redacted.get(key)).as(key).isNotEqualTo(otherKey.get(key));
        }
        assertThat(redacted).containsEntry("fileName", "report.csv");
    }

    @Test
    void productionRequiresAConfiguredSecret() {
        org.springframework.mock.env.MockEnvironment prod = new org.springframework.mock.env.MockEnvironment();
        prod.setActiveProfiles("prod");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new AuditRedactionPolicy("", prod))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new AuditRedactionPolicy("configured", prod).redact(Map.of("email", "a@b.vn")).get("email"))
                .asString().startsWith("hmac-sha256:");
    }

    @Test
    void truncatesOversizedCollectionsAndDepthInsteadOfPersistingRequestBodies() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("items", java.util.stream.IntStream.range(0, 150).boxed().toList());
        Map<String, Object> deep = new LinkedHashMap<>();
        Map<String, Object> cursor = deep;
        for (int index = 0; index < 9; index++) {
            Map<String, Object> next = new LinkedHashMap<>();
            cursor.put("next", next);
            cursor = next;
        }
        input.put("deep", deep);

        Map<String, Object> redacted = policy.redact(input);

        assertThat((List<?>) redacted.get("items")).hasSize(100);
        assertThat(redacted.toString()).contains("[DEPTH_LIMIT]");
    }
}

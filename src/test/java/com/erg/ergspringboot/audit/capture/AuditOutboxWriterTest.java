package com.erg.ergspringboot.audit.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecord;
import com.erg.ergspringboot.audit.internal.AuditRedactionPolicy;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

class AuditOutboxWriterTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final AuditOutboxWriter writer = new AuditOutboxWriter(jdbc, new ObjectMapper(),
            new AuditRedactionPolicy(), transactions);

    private AuditRecord record(Map<String, Object> details) {
        return new AuditRecord("tenant-a", "u-1", "TENANT", "tenant-a", "POST /api/v1/users", "USERS", null,
                AuditOutcome.SUCCESS, "req-1", "req-1", null, details, null, OffsetDateTime.now(), null);
    }

    @Test
    void secretsAreMasked() {
        writer.write(record(Map.of(
                "password", "hunter2",
                "params", Map.of("refreshToken", "tok-123", "otp", "654321", "fileContent", "x", "name", "Nguyen Van A", "className", "Lop 1A"))));

        Invocation insert = mockingDetails(jdbc).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("update")).findFirst().orElseThrow();
        String json = (String) insert.getArguments()[3];
        assertThat(json).doesNotContain("hunter2", "tok-123", "654321").contains("[REDACTED]").contains("Lop 1A");
        // "name" is a PII key (pseudonymised, AuditRedactionPolicy PII_EXACT_KEYS), so a person's name must not appear.
        assertThat(json).doesNotContain("Nguyen Van A");
    }

    @Test
    void writeNeverThrowsWhenTheDatabaseFails() {
        when(transactions.getTransaction(any())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> writer.write(record(Map.of()))).doesNotThrowAnyException();
    }

    @Test
    void resourceIdIsTheLastPathVariableOrTheIdArgument() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notification-campaigns/c-1/send");
        assertThat(AuditOutboxWriter.resourceIdOf(request)).isNull();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, new java.util.LinkedHashMap<>(Map.of("id", "c-1")));
        assertThat(AuditOutboxWriter.resourceIdOf(request)).isEqualTo("c-1");
        assertThat(AuditOutboxWriter.argumentResourceId(Map.of("input", Map.of("id", "q-9")))).isEqualTo("q-9");
        assertThat(AuditOutboxWriter.argumentResourceId(Map.of("name", "x"))).isNull();
    }
}

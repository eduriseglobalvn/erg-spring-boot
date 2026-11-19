package com.erg.ergspringboot.audit.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

class MutationAuditInterceptorTest {
    private final AuditOutboxWriter writer = mock(AuditOutboxWriter.class);
    private final MutationAuditInterceptor interceptor = new MutationAuditInterceptor(writer);
    private HandlerMethod handler;

    public static class Sample {
        public void create() {}
    }

    @BeforeEach
    void setUp() throws Exception {
        handler = new HandlerMethod(new Sample(), Sample.class.getMethod("create"));
        CaptureTestSupport.signIn();
    }

    @AfterEach
    void tearDown() {
        CaptureTestSupport.signOut();
    }

    private AuditRecord run(String method, String uri, String pattern, int status, Exception failure) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
        request.setParameter("name", "Lop 1A");
        request.setParameter("password", "hunter2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        interceptor.afterCompletion(request, response, handler, failure);
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(writer, org.mockito.Mockito.atMost(1)).write(captor.capture());
        return captor.getAllValues().isEmpty() ? null : captor.getValue();
    }

    @Test
    void restPostIsAudited() {
        AuditRecord record = run("POST", "/api/v1/users/u9/reset", "/api/v1/users/{id}/reset", 200, null);

        assertThat(record).isNotNull();
        assertThat(record.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(record.tenantId()).isEqualTo("tenant-a");
        assertThat(record.actorId()).isEqualTo("u-1");
        assertThat(record.action()).isEqualTo("users.reset");
        assertThat(record.details()).containsEntry("pattern", "/api/v1/users/{id}/reset").containsEntry("method", "POST");
        assertThat(record.resourceType()).isEqualTo("USERS");
        assertThat(record.details()).containsEntry("status", 200).containsKey("roles");
    }

    @Test
    void deniedIsAudited() {
        AuditRecord record = run("DELETE", "/api/v1/users/u9", "/api/v1/users/{id}", 403, null);

        assertThat(record.outcome()).isEqualTo(AuditOutcome.DENIED);
    }

    @Test
    void unauthenticatedIsNotAudited() {
        CaptureTestSupport.signOut();

        run("POST", "/api/v1/auth/login", "/api/v1/auth/login", 401, null);

        verifyNoInteractions(writer);
    }

    @Test
    void rolledBackActionLeavesNoAudit() {
        // The business transaction rolled back (HTTP 500): there must be no SUCCESS record for it.
        AuditRecord record = run("POST", "/api/v1/users/u9/reset", "/api/v1/users/{id}/reset", 500,
                new IllegalStateException("rollback"));

        assertThat(record.outcome()).isEqualTo(AuditOutcome.FAILURE);
    }

    @Test
    void getAndSelfServiceAndAlreadyAuditedAreSkipped() {
        run("GET", "/api/v1/users", "/api/v1/users", 200, null);
        run("PATCH", "/api/v1/users/me", "/api/v1/users/me", 200, null);
        run("POST", "/api/v1/lms/attempts/a1/submit", "/api/v1/lms/attempts/{attemptId}/submit", 200, null);
        run("POST", "/api/v1/users/u1/role-assignments", "/api/v1/users/{userId}/role-assignments", 200, null);

        verify(writer, never()).write(any());
    }
}

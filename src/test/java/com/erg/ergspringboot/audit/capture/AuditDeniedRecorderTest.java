package com.erg.ergspringboot.audit.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

class AuditDeniedRecorderTest {
    private final AuditOutboxWriter writer = mock(AuditOutboxWriter.class);
    private final AuditDeniedRecorder recorder = new AuditDeniedRecorder(writer);

    @AfterEach
    void tearDown() {
        CaptureTestSupport.signOut();
    }

    @Test
    void filterLayerForbiddenWriteIsRecordedAsDenied() {
        CaptureTestSupport.signIn();

        recorder.record(new MockHttpServletRequest("POST", "/api/v1/users/u9/lock"));

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(writer).write(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(captor.getValue().action()).isEqualTo("POST /api/v1/users/u9/lock");
    }

    @Test
    void anonymousAndReadRequestsAreNotRecorded() {
        recorder.record(new MockHttpServletRequest("POST", "/api/v1/users/u9/lock"));
        CaptureTestSupport.signIn();
        recorder.record(new MockHttpServletRequest("GET", "/api/v1/users"));

        verify(writer, never()).write(any());
    }
}

package com.erg.ergspringboot.service.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.model.outbox.OutboxEvent;
import com.erg.ergspringboot.repository.outbox.OutboxEventRepository;
import com.erg.ergspringboot.service.auth.SmtpAccountMailSender;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;

@ExtendWith(MockitoExtension.class)
class TransactionalOutboxWorkerTest {
    @Mock private OutboxEventRepository repository;
    @Mock private SmtpAccountMailSender smtp;

    @Test
    void transientProviderFailureMovesEventToRetryWithLeaseCleared() {
        OutboxEvent event = event();
        when(repository.findDueForUpdate(any(), any(Integer.class))).thenReturn(List.of(event));
        when(repository.findById("event-1")).thenReturn(Optional.of(event));
        doThrow(new IllegalStateException("smtp timeout"))
                .when(smtp).sendTeacherInvite("teacher@example.com", "Teacher", "secret");
        TransactionalOutboxWorker worker = new TransactionalOutboxWorker(
                repository, smtp, new OutboxPayloadCipher("test-key"), new ImmediateTransactionManager());

        worker.runDueBatch();

        assertThat(event.getStatus()).isEqualTo("retry");
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLeaseUntil()).isNull();
        assertThat(event.getLastError()).contains("smtp timeout");
    }

    private OutboxEvent event() {
        OutboxEvent event = new OutboxEvent();
        event.setId("event-1"); event.setTenantId("tenant-a");
        event.setEventType("ACCOUNT_TEACHER_INVITE"); event.setStatus("pending");
        event.setAvailableAt(OffsetDateTime.now()); event.setCreatedAt(OffsetDateTime.now());
        event.setPayload(Map.of("recipient", "teacher@example.com", "fullName", "Teacher",
                "secret", new OutboxPayloadCipher("test-key").encrypt("secret")));
        return event;
    }

    private static final class ImmediateTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}

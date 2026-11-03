package com.erg.ergspringboot.service.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.model.outbox.OutboxEvent;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.repository.outbox.OutboxEventRepository;
import com.erg.ergspringboot.service.auth.SmtpAccountMailSender;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
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

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void claimsCrossTenantBatchInMaintenanceScopeAndRestoresCallerContext() {
        TenantContext caller = TenantContext.of("outer-tenant", "outer-user", "admin");
        TenantContext.set(caller);
        when(repository.findDueForUpdate(any(), any(Integer.class))).thenAnswer(invocation -> {
            assertThat(TenantContext.current()).isNotNull();
            assertThat(TenantContext.current().isMaintenance()).isTrue();
            return List.of();
        });
        TransactionalOutboxWorker worker = new TransactionalOutboxWorker(
                repository, smtp, new OutboxPayloadCipher("test-key"), new ImmediateTransactionManager());

        assertThat(worker.runDueBatch()).isZero();

        assertThat(TenantContext.current()).isSameAs(caller);
    }

    @Test
    void transientProviderFailureMovesEventToRetryWithLeaseCleared() {
        OutboxEvent event = event();
        when(repository.findDueForUpdate(any(), any(Integer.class))).thenReturn(List.of(event));
        when(repository.renewLease(eq("event-1"), eq(TransactionalOutboxWorker.WORKER_ID), any())).thenReturn(1);
        when(repository.findLeasedForUpdate("event-1", TransactionalOutboxWorker.WORKER_ID)).thenReturn(Optional.of(event));
        doThrow(new IllegalStateException("smtp timeout"))
                .when(smtp).sendTeacherInvite("teacher@example.com", "Teacher", "secret");
        TransactionalOutboxWorker worker = new TransactionalOutboxWorker(
                repository, smtp, new OutboxPayloadCipher("test-key"), new ImmediateTransactionManager());

        worker.runDueBatch();

        assertThat(event.getStatus()).isEqualTo("retry");
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLeaseUntil()).isNull();
        assertThat(event.getLastError()).isEqualTo("IllegalStateException");
    }

    @Test
    void decryptsRecipientWrittenByTheTransactionalMailSender() {
        OutboxEvent event = event();
        OutboxPayloadCipher cipher = new OutboxPayloadCipher("test-key");
        event.setPayload(Map.of("recipientCiphertext", cipher.encrypt("teacher@example.com"), "fullName", "Teacher",
                "secret", cipher.encrypt("secret")));
        when(repository.findDueForUpdate(any(), any(Integer.class))).thenReturn(List.of(event));
        when(repository.findById("event-1")).thenReturn(Optional.of(event));
        TransactionalOutboxWorker worker = new TransactionalOutboxWorker(
                repository, smtp, cipher, new ImmediateTransactionManager());

        worker.runDueBatch();

        org.mockito.Mockito.verify(smtp).sendTeacherInvite("teacher@example.com", "Teacher", "secret");
        assertThat(event.getStatus()).isEqualTo("sent");
    }

    @Test
    void claimStampsPerInstanceOwnerAndSkipsRowsWhoseLeaseWasReclaimed() {
        OutboxEvent event = event();
        when(repository.findDueForUpdate(any(), any(Integer.class))).thenReturn(List.of(event));
        when(repository.renewLease(eq("event-1"), eq(TransactionalOutboxWorker.WORKER_ID), any())).thenReturn(0);
        TransactionalOutboxWorker worker = new TransactionalOutboxWorker(
                repository, smtp, new OutboxPayloadCipher("test-key"), new ImmediateTransactionManager());

        worker.runDueBatch();

        assertThat(TransactionalOutboxWorker.WORKER_ID).isNotBlank().contains(":").hasSizeLessThanOrEqualTo(96);
        verify(repository).markLockedBy(List.of("event-1"), TransactionalOutboxWorker.WORKER_ID);
        verifyNoInteractions(smtp);
        verify(repository, never()).findLeasedForUpdate(any(), any());
    }

    @Test
    void resultIsNotRecordedAfterLosingTheLease() {
        OutboxEvent event = event();
        when(repository.findDueForUpdate(any(), any(Integer.class))).thenReturn(List.of(event));
        when(repository.renewLease(eq("event-1"), eq(TransactionalOutboxWorker.WORKER_ID), any())).thenReturn(1);
        when(repository.findLeasedForUpdate("event-1", TransactionalOutboxWorker.WORKER_ID)).thenReturn(Optional.empty());
        TransactionalOutboxWorker worker = new TransactionalOutboxWorker(
                repository, smtp, new OutboxPayloadCipher("test-key"), new ImmediateTransactionManager());

        worker.runDueBatch();

        assertThat(event.getAttempts()).isZero();
        verify(repository, never()).save(any());
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

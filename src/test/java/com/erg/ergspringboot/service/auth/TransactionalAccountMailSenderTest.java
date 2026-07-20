package com.erg.ergspringboot.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.model.outbox.OutboxEvent;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.repository.outbox.OutboxEventRepository;
import com.erg.ergspringboot.service.outbox.OutboxPayloadCipher;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionalAccountMailSenderTest {
    @Mock private OutboxEventRepository repository;

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void writesTenantScopedInviteIntentInsteadOfCallingSmtp() {
        TenantContext.set(TenantContext.of("tenant-a", "admin-1", "lms"));
        when(repository.findByTenantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        TransactionalAccountMailSender sender = new TransactionalAccountMailSender(repository, new OutboxPayloadCipher("test-key"));

        sender.sendTeacherInvite("teacher@example.com", "Teacher", "one-time");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertThat(event.getTenantId()).isEqualTo("tenant-a");
        assertThat(event.getEventType()).isEqualTo("ACCOUNT_TEACHER_INVITE");
        assertThat(event.getStatus()).isEqualTo("pending");
        assertThat(event.getPayload()).containsEntry("recipient", "teacher@example.com");
    }

    @Test
    void duplicateIntentIsNotInsertedTwice() {
        when(repository.findByTenantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.of(new OutboxEvent()));
        TransactionalAccountMailSender sender = new TransactionalAccountMailSender(repository, new OutboxPayloadCipher("test-key"));

        sender.sendRecoveryEmailCode("teacher@example.com", "Teacher", "123456");

        verify(repository, never()).save(any());
    }
}

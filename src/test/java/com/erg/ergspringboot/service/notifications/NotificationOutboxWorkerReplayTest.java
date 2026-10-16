package com.erg.ergspringboot.service.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.notifications.NotificationResponses.DeadLetterReplayResponse;
import com.erg.ergspringboot.model.notifications.NotificationOutboxEntity;
import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.notifications.delivery.NotificationDeliveryAdapterRegistry;
import com.erg.ergspringboot.repository.notifications.NotificationDeliveryAttemptRepository;
import com.erg.ergspringboot.repository.notifications.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.repository.notifications.NotificationMessageRepository;
import com.erg.ergspringboot.repository.notifications.NotificationOutboxRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * BE-016 dead-letter replay: an operator must be able to return exhausted ("dead") notification-outbox
 * rows to the retry queue, and the reset must hand control back to the normal retry/backoff state
 * machine (attempt_count zeroed, lease cleared) without duplicating anything at replay time.
 */
@ExtendWith(MockitoExtension.class)
class NotificationOutboxWorkerReplayTest {

    @Mock
    private NotificationOutboxRepository outboxRepository;
    @Mock
    private NotificationMessageRepository messageRepository;
    @Mock
    private NotificationDeliveryAttemptRepository deliveryAttemptRepository;
    @Mock
    private NotificationDeviceRegistrationRepository deviceRepository;
    @Mock
    private NotificationDeliveryAdapterRegistry deliveryAdapters;
    @Mock
    private NotificationMetrics metrics;
    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    void replayResetsDeadRowsToRetryWithFreshAttemptState() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        NotificationOutboxEntity dead = deadRow("outbox-1", "tenant_a");
        when(outboxRepository.findDeadForUpdateByTenant(eq("tenant_a"), anyInt()))
                .thenReturn(List.of(dead));

        DeadLetterReplayResponse response = worker().replayDeadLetters("tenant_a", 100);

        assertThat(response.requeued()).isEqualTo(1);
        assertThat(dead.getStatus()).isEqualTo("retry");
        assertThat(dead.getAttemptCount()).isZero();
        assertThat(dead.getNextAttemptAt()).isNotNull();
        assertThat(dead.getLockedAt()).isNull();
        assertThat(dead.getLockedBy()).isNull();
        verify(outboxRepository).save(dead);
        // Replay must not itself dispatch — it only requeues; the scheduled worker sends later.
        verify(deliveryAdapters, never()).deliver(any());
    }

    @Test
    void replayIgnoresBlankTenantAndDoesNotTouchTheStore() {
        DeadLetterReplayResponse response = worker().replayDeadLetters("  ", 100);

        assertThat(response.requeued()).isZero();
        verify(outboxRepository, never()).findDeadForUpdateByTenant(any(), anyInt());
        verify(outboxRepository, never()).save(any());
    }

    private NotificationOutboxEntity deadRow(String id, String tenantId) {
        NotificationOutboxEntity outbox = new NotificationOutboxEntity();
        outbox.setId(id);
        outbox.setTenantId(tenantId);
        outbox.setNotificationId("notification-1");
        outbox.setTargetKind("registration_token");
        outbox.setTargetRef("token-ref");
        outbox.setStatus("dead");
        outbox.setAttemptCount(8);
        outbox.setMaxAttempts(8);
        outbox.setNextAttemptAt(OffsetDateTime.now().minusHours(1));
        outbox.setLockedAt(OffsetDateTime.now().minusHours(1));
        outbox.setLockedBy("erg-spring");
        return outbox;
    }

    private NotificationOutboxWorker worker() {
        return new NotificationOutboxWorker(
                outboxRepository,
                messageRepository,
                deliveryAttemptRepository,
                deviceRepository,
                deliveryAdapters,
                new NotificationPushProperties(),
                transactionManager,
                metrics
        );
    }
}

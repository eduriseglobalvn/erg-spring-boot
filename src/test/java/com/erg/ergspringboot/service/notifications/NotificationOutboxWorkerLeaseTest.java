package com.erg.ergspringboot.service.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.notifications.NotificationResponses.OutboxRunResponse;
import com.erg.ergspringboot.model.notifications.NotificationMessageEntity;
import com.erg.ergspringboot.model.notifications.NotificationOutboxEntity;
import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.notifications.delivery.NotificationDeliveryAdapterRegistry;
import com.erg.ergspringboot.notifications.push.SendResult;
import com.erg.ergspringboot.repository.notifications.NotificationDeliveryAttemptRepository;
import com.erg.ergspringboot.repository.notifications.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.repository.notifications.NotificationMessageRepository;
import com.erg.ergspringboot.repository.notifications.NotificationOutboxRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** C15: per-instance worker id, lease renewal before send, and results recorded only while the lease is ours. */
@ExtendWith(MockitoExtension.class)
class NotificationOutboxWorkerLeaseTest {

    @Mock private NotificationOutboxRepository outboxRepository;
    @Mock private NotificationMessageRepository messageRepository;
    @Mock private NotificationDeliveryAttemptRepository deliveryAttemptRepository;
    @Mock private NotificationDeviceRegistrationRepository deviceRepository;
    @Mock private NotificationDeliveryAdapterRegistry deliveryAdapters;
    @Mock private NotificationMetrics metrics;
    @Mock private PlatformTransactionManager transactionManager;

    @Test
    void workerIdIsUniquePerInstanceNotAConstant() {
        assertThat(NotificationOutboxWorker.WORKER_ID).isNotEqualTo("erg-spring").contains(":").hasSizeLessThanOrEqualTo(80);
    }

    @Test
    void rowWhoseLeaseWasReclaimedIsNotSent() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        NotificationOutboxEntity row = row("outbox-1");
        when(outboxRepository.findDueForUpdate(any(), any(), anyInt())).thenReturn(List.of(row));
        when(outboxRepository.renewLease(eq("outbox-1"), eq(NotificationOutboxWorker.WORKER_ID), any())).thenReturn(0);

        OutboxRunResponse response = worker().runDueBatch();

        assertThat(response.sent()).isZero();
        verify(deliveryAdapters, never()).deliver(any());
    }

    @Test
    void resultIsDiscardedWhenLeaseIsLostDuringSend() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        NotificationOutboxEntity row = row("outbox-1");
        when(outboxRepository.findDueForUpdate(any(), any(), anyInt())).thenReturn(List.of(row));
        when(outboxRepository.renewLease(eq("outbox-1"), eq(NotificationOutboxWorker.WORKER_ID), any())).thenReturn(1);
        NotificationMessageEntity message = new NotificationMessageEntity();
        message.setId("notification-1");
        when(messageRepository.findById("notification-1")).thenReturn(Optional.of(message));
        when(deliveryAdapters.deliver(any())).thenReturn(SendResult.success(200, "msg", 3));
        when(outboxRepository.findLeasedForUpdate("outbox-1", NotificationOutboxWorker.WORKER_ID)).thenReturn(Optional.empty());

        OutboxRunResponse response = worker().runDueBatch();

        assertThat(response.sent()).isZero();
        verify(deliveryAttemptRepository, never()).save(any());
        // only the claim itself saved the row; the stale result never overwrote another owner's state
        verify(outboxRepository, org.mockito.Mockito.times(1)).save(row);
        assertThat(row.getLockedBy()).isEqualTo(NotificationOutboxWorker.WORKER_ID);
    }

    private NotificationOutboxEntity row(String id) {
        NotificationOutboxEntity outbox = new NotificationOutboxEntity();
        outbox.setId(id);
        outbox.setTenantId("tenant_a");
        outbox.setNotificationId("notification-1");
        outbox.setTargetKind("topic");
        outbox.setTargetRef("t_tenant_a_portal_lms");
        outbox.setStatus("pending");
        outbox.setAttemptCount(0);
        outbox.setMaxAttempts(8);
        outbox.setNextAttemptAt(OffsetDateTime.now().minusMinutes(1));
        return outbox;
    }

    private NotificationOutboxWorker worker() {
        return new NotificationOutboxWorker(outboxRepository, messageRepository, deliveryAttemptRepository,
                deviceRepository, deliveryAdapters, new NotificationPushProperties(), transactionManager, metrics);
    }
}

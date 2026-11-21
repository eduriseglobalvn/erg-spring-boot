package com.erg.ergspringboot.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.notifications.NotificationResponses.OutboxRunResponse;
import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.notifications.delivery.NotificationDeliveryAdapterRegistry;
import com.erg.ergspringboot.notifications.delivery.NotificationDeliveryRequest;
import com.erg.ergspringboot.notifications.persistence.NotificationDeliveryAttemptRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationMessageEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationMessageRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationOutboxEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationOutboxRepository;
import com.erg.ergspringboot.notifications.push.SendResult;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Delivery runs on a pool of 8: every row is processed, one failing row is isolated, concurrency stays bounded. */
class NotificationOutboxWorkerParallelTest {

    private final NotificationOutboxRepository outbox = mock(NotificationOutboxRepository.class);
    private final NotificationMessageRepository messages = mock(NotificationMessageRepository.class);
    private final NotificationDeliveryAdapterRegistry adapters = mock(NotificationDeliveryAdapterRegistry.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);

    @Test
    void everyRowIsProcessedAFailingRowDoesNotStopTheOthersAndConcurrencyNeverExceedsEight() {
        int rows = 60;
        Map<String, NotificationOutboxEntity> byId = new HashMap<>();
        List<NotificationOutboxEntity> due = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            NotificationOutboxEntity row = row("o" + i);
            byId.put(row.getId(), row);
            due.add(row);
        }
        NotificationPushProperties properties = new NotificationPushProperties();
        properties.getWorker().setPollSize(500);
        when(tx.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(outbox.findDueForUpdate(any(), any(), anyInt())).thenReturn(due);
        when(outbox.renewLease(anyString(), anyString(), any())).thenReturn(1);
        when(outbox.findLeasedForUpdate(anyString(), anyString())).thenAnswer(call -> Optional.of(byId.get(call.<String>getArgument(0))));
        NotificationMessageEntity message = new NotificationMessageEntity();
        message.setId("n1");
        when(messages.findAllById(any())).thenReturn(List.of(message));
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicInteger delivered = new AtomicInteger();
        when(adapters.deliver(any(NotificationDeliveryRequest.class))).thenAnswer(call -> {
            int now = inFlight.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(15);
                if (delivered.incrementAndGet() == 7) {
                    throw new IllegalStateException("provider blew up");
                }
                return SendResult.success(200, "m", 1);
            } finally {
                inFlight.decrementAndGet();
            }
        });
        NotificationOutboxWorker worker = new NotificationOutboxWorker(outbox, messages,
                mock(NotificationDeliveryAttemptRepository.class), mock(NotificationDeviceRegistrationRepository.class),
                adapters, properties, tx, mock(NotificationMetrics.class));

        OutboxRunResponse response = worker.runDueBatch();

        assertThat(delivered.get()).isEqualTo(rows);
        assertThat(response.processed()).isEqualTo(rows);
        assertThat(response.sent()).isEqualTo(rows - 1);
        assertThat(peak.get()).isBetween(2, NotificationOutboxWorker.PARALLELISM);
        worker.shutdown();
    }

    private static NotificationOutboxEntity row(String id) {
        NotificationOutboxEntity row = new NotificationOutboxEntity();
        row.setId(id);
        row.setTenantId("tenant_a");
        row.setNotificationId("n1");
        row.setTargetKind("topic");
        row.setTargetRef("t_tenant_a_portal_lms");
        row.setStatus("pending");
        row.setAttemptCount(0);
        row.setMaxAttempts(8);
        row.setNextAttemptAt(OffsetDateTime.now().minusMinutes(1));
        return row;
    }
}

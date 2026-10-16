package com.erg.ergspringboot.notifications;

import com.erg.ergspringboot.common.request.notifications.NotificationRequests.AdminSendRequest;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.AdminSendResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.AdminStatsResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.DeadLetterReplayResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.OutboxRunResponse;
import com.erg.ergspringboot.repository.notifications.NotificationOutboxRepository;
import com.erg.ergspringboot.service.notifications.NotificationCommand;
import com.erg.ergspringboot.service.notifications.NotificationCommandResult;
import com.erg.ergspringboot.service.notifications.NotificationCommandService;
import com.erg.ergspringboot.service.notifications.NotificationOutboxWorker;
import org.springframework.stereotype.Service;

/** Public application boundary for notification administration. */
@Service
public class NotificationAdminOperations {
    private final NotificationCommandService commands;
    private final NotificationOutboxWorker worker;
    private final NotificationOutboxRepository outbox;

    public NotificationAdminOperations(
            NotificationCommandService commands,
            NotificationOutboxWorker worker,
            NotificationOutboxRepository outbox
    ) {
        this.commands = commands;
        this.worker = worker;
        this.outbox = outbox;
    }

    public AdminSendResponse send(String tenantId, String actorUserId, AdminSendRequest body) {
        NotificationCommandResult result = commands.enqueue(new NotificationCommand(
                tenantId,
                actorUserId,
                body.idempotencyKey(),
                body.eventType(),
                body.sourceType(),
                body.sourceId(),
                body.title(),
                body.bodyPreview(),
                body.actionUrl(),
                body.priority(),
                body.portal(),
                body.userIds(),
                body.topicName(),
                body.data()
        ));
        return new AdminSendResponse(result.notificationId(), result.recipientCount(), result.outboxCount());
    }

    public AdminStatsResponse stats(String tenantId) {
        return new AdminStatsResponse(
                outbox.countByTenantIdAndStatus(tenantId, "pending"),
                outbox.countByTenantIdAndStatus(tenantId, "retry"),
                outbox.countByTenantIdAndStatus(tenantId, "sent"),
                outbox.countByTenantIdAndStatus(tenantId, "dead")
        );
    }

    public OutboxRunResponse runOutbox(String tenantId) {
        return worker.runDueBatch(tenantId);
    }

    public DeadLetterReplayResponse replayDeadLetters(String tenantId, int limit) {
        return worker.replayDeadLetters(tenantId, limit);
    }
}

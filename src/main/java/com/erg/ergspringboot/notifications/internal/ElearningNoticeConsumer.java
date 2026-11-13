package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.elearning.ElearningNotice;
import java.util.Map;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Turns student discussion replies and Hộp thư messages into in-app notifications for the "elearning" portal. */
@Component
public class ElearningNoticeConsumer {

    private final NotificationCommandService commands;

    public ElearningNoticeConsumer(NotificationCommandService commands) {
        this.commands = commands;
    }

    @ApplicationModuleListener
    public void on(ElearningNotice notice) {
        if (notice.recipientUserIds() == null || notice.recipientUserIds().isEmpty()) {
            return;
        }
        commands.enqueue(new NotificationCommand(notice.tenantId(), notice.actorId(), notice.eventId(), notice.eventType(),
                notice.sourceType(), notice.sourceId(), notice.title(), notice.preview(), null, "normal", "elearning",
                notice.recipientUserIds(), null, Map.of("sourceId", notice.sourceId())));
    }
}

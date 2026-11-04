package com.erg.ergspringboot.notifications.internal;

public record NotificationCommandResult(
        String notificationId,
        int recipientCount,
        int outboxCount
) {
}

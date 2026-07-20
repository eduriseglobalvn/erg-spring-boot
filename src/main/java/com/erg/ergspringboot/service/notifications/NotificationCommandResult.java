package com.erg.ergspringboot.service.notifications;

public record NotificationCommandResult(
        String notificationId,
        int recipientCount,
        int outboxCount
) {
}

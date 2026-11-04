package com.erg.ergspringboot.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NotificationBoundaryContractTest {

    @Test
    void workerRoutesOnlyThroughProviderRegistry() throws Exception {
        String worker = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/notifications/internal/NotificationOutboxWorker.java"));
        assertThat(worker).doesNotContain("FirebasePushSender", "FirebasePushPayload", "switch (outbox.getTargetKind())");
        assertThat(worker).contains("NotificationDeliveryAdapterRegistry", "deliveryAdapters.deliver");
    }

    @Test
    void calendarOwnsTheProjectionAdapterAndDoesNotReachIntoNotifications() throws Exception {
        Path calendarSource = Path.of("src/main/java/com/erg/ergspringboot/calendar");
        try (var paths = Files.walk(calendarSource)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    assertThat(Files.readString(path))
                            .as("calendar source %s", path)
                            .doesNotContain("notifications.internal.NotificationCommandService");
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }
}

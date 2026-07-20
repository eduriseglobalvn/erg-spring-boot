package com.erg.ergspringboot.notifications;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NotificationAdminBoundaryTest {
    @Test
    void controllerUsesModuleFacadeInsteadOfPersistenceOrLegacyCommands() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/controller/notifications/NotificationAdminController.java"));
        assertTrue(source.contains("NotificationAdminOperations"));
        assertFalse(source.contains("repository.notifications"));
        assertFalse(source.contains("NotificationCommandService"));
        assertFalse(source.contains("NotificationOutboxWorker"));
    }
}

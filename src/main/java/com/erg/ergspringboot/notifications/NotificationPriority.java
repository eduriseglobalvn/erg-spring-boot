package com.erg.ergspringboot.notifications;

import java.util.Locale;

public enum NotificationPriority {
    INFO, IMPORTANT, URGENT;

    /**
     * Reads notification_messages.priority: campaign rows hold the enum name, legacy producers wrote
     * 'normal'/'high' (and FE fixtures 'urgent'). Unknown or blank values read as INFO.
     */
    public static NotificationPriority fromStored(String value) {
        if (value == null) return INFO;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "important", "high" -> IMPORTANT;
            case "urgent" -> URGENT;
            default -> INFO;
        };
    }
}

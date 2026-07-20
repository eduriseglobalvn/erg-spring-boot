package com.erg.ergspringboot.platform.security;

/** Explicit context factory for scheduled, outbox and other non-user work. */
public final class SystemActorContext {

    private SystemActorContext() {
    }

    public static UserAccessContext forTask(String task, String tenantId) {
        if (task == null || task.isBlank()) {
            throw new IllegalArgumentException("task must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        return new UserAccessContext(
                "system:" + task.trim(),
                tenantId.trim(),
                java.util.List.of("*"),
                java.util.List.of("*"),
                null,
                null
        );
    }
}

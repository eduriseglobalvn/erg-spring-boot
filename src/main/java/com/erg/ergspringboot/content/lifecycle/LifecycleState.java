package com.erg.ergspringboot.content.lifecycle;

import java.util.Locale;

public enum LifecycleState {
    DRAFT,
    IN_REVIEW,
    APPROVED,
    PUBLISHED,
    ARCHIVED;

    public static LifecycleState parse(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("targetState is required");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported targetState: " + value);
        }
    }
}

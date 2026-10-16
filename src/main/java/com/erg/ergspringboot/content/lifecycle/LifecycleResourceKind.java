package com.erg.ergspringboot.content.lifecycle;

import java.util.Locale;

/** Canonical authoring resources that share the content lifecycle. */
public enum LifecycleResourceKind {
    CURRICULUM_SUBJECT,
    CURRICULUM_LEVEL,
    CURRICULUM_TOPIC,
    CONTENT_ITEM,
    QUESTION,
    QUIZ;

    public static LifecycleResourceKind parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("resourceKind is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (normalized.equals("RESOURCE") || normalized.equals("LEARNING_CONTENT")) return CONTENT_ITEM;
        if (normalized.equals("CURRICULUM")) return CURRICULUM_TOPIC;
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported resourceKind: " + value);
        }
    }
}

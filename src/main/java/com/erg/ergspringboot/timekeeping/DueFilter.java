package com.erg.ergspringboot.timekeeping;

/** Inbox filter on the current step's due time: already past it, or due within 24 hours. */
public enum DueFilter {
    OVERDUE, SOON
}

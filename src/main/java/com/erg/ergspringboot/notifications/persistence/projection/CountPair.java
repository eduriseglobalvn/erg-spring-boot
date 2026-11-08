package com.erg.ergspringboot.notifications.persistence.projection;

/** Two aggregate counts from one statement; SQL sum() over no rows is null, read as 0. */
public record CountPair(Long first, Long second) {
    public long firstOrZero() { return first == null ? 0 : first; }
    public long secondOrZero() { return second == null ? 0 : second; }
}

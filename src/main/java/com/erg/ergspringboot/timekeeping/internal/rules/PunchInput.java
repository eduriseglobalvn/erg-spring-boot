package com.erg.ergspringboot.timekeeping.internal.rules;

/** What the device reported for one punch; {@code reason} is the free-text note for an outside-zone punch. */
public record PunchInput(Double lat, Double lng, Double accuracyM, boolean mockReported, boolean remote, String reason) {}

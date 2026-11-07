package com.erg.ergspringboot.timekeeping.internal.rules;

import com.erg.ergspringboot.timekeeping.PunchStatus;

/** Outcome of {@link PunchValidator#check}; {@code distanceM} is null when no location was involved, {@code messageVi} null when VALID. */
public record PunchCheck(PunchStatus verdict, Integer distanceM, String messageVi) {}

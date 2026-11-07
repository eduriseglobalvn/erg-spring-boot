package com.erg.ergspringboot.calendar;

import java.time.Instant;

/** One published teaching period of a teacher, for callers that have no principal (jobs, timekeeping). */
public record TeacherPeriod(Instant start, Instant end, String className, String subject, String room) {}

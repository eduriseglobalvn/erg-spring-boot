package com.erg.ergspringboot.timekeeping;

import java.time.LocalDate;

/**
 * One week column of a teaching-hours month. {@code from}..{@code to} is every calendar day counted in the column
 * (stray Saturday/Sunday included); {@code label} is the printed header such as {@code 05-09}.
 */
public record TeachingWeek(int index, LocalDate from, LocalDate to, String label) {}

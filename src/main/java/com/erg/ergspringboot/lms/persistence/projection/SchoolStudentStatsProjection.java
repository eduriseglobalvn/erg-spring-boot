package com.erg.ergspringboot.lms.persistence.projection;

/** Counts over one school's students (or a filtered slice of them), computed in SQL. */
public record SchoolStudentStatsProjection(
        Long total,
        Long atRisk,
        Long studying,
        Long profileComplete,
        Long assigned
) {
}

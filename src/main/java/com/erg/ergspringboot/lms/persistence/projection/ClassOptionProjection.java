package com.erg.ergspringboot.lms.persistence.projection;

public record ClassOptionProjection(
        String id,
        String schoolId,
        String name,
        String grade,
        String academicYear,
        String status
) {
}

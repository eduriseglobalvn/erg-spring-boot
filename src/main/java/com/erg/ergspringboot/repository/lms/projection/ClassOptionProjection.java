package com.erg.ergspringboot.repository.lms.projection;

public record ClassOptionProjection(
        String id,
        String schoolId,
        String name,
        String grade,
        String academicYear,
        String status
) {
}

package com.erg.ergspringboot.model.courses;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/courses/domain/entity/course.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseEnrollment {

    private String id;

    private String courseId;

    private String userId;

    private OffsetDateTime enrolledAt;

    private OffsetDateTime completedAt;

    private Integer progressPercent;
}

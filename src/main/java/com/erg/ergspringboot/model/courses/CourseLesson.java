package com.erg.ergspringboot.model.courses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/courses/domain/entity/course.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseLesson {

    private String id;

    private String courseId;

    private Integer order;

    private String title;

    private String content;

    private Integer durationMinutes;

    private String videoUrl;
}

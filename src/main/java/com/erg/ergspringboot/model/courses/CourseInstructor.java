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
public class CourseInstructor {

    private String id;

    private String name;

    private String bio;

    private String avatarUrl;

    private String title;
}

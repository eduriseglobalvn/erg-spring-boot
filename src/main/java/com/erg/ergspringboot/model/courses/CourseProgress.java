package com.erg.ergspringboot.model.courses;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.erg.ergspringboot.model.BaseEntity;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go relational core models.
 */
@Entity
@Table(name = "course_progress")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class CourseProgress extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    private String courseId;

    private String lessonId;

    private Boolean isCompleted;

    private Double progressPercent;

    private OffsetDateTime completedAt;

    private OffsetDateTime lastAccessedAt;

    private Double score;
}

package com.erg.ergspringboot.lms.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One taught period in the electronic class log; absent until the teacher edits or confirms it. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "lms_lesson_log_entries")
public class LessonLogEntry extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;

    @Column(name = "lesson_date", nullable = false)
    private LocalDate lessonDate;

    @Column(name = "period_no", nullable = false)
    private Short periodNo;

    @Column(name = "class_id", length = 100, nullable = false)
    private String classId;

    @Column(name = "subject_id", length = 100, nullable = false)
    private String subjectId;

    @Column(name = "teacher_user_id", length = 36, nullable = false)
    private String teacherUserId;

    /** Snapshot of the curriculum-plan position when the row was created. */
    @Column(name = "ppct_period", nullable = false)
    private Integer ppctPeriod;

    @Column(name = "actual_period", nullable = false)
    private Integer actualPeriod;

    @Column(name = "comment", length = 500)
    private String comment;

    /** PENDING | CONFIRMED. */
    @Column(name = "status", length = 16, nullable = false)
    private String status = "PENDING";

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_by", length = 36)
    private String confirmedBy;

    @Column(name = "late", nullable = false)
    private boolean late;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}

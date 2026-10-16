package com.erg.ergspringboot.model.lms;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "lms_assignments",
        indexes = {
                @Index(name = "idx_lms_assignments_tenant_academic_class", columnList = "tenant_id, academic_class_id"),
                @Index(name = "idx_lms_assignments_tenant_assigned_by", columnList = "tenant_id, assigned_by"),
                @Index(name = "idx_lms_assignments_tenant_quiz", columnList = "tenant_id, quiz_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Assignment extends BaseEntity {

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "academic_class_id", nullable = false)
    private String academicClassId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_class_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
    @ToString.Exclude
    private Classroom classroom;

    @Column(name = "quiz_id")
    private String quizId;

    @Column(name = "subject_id")
    private String subjectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "student_ids", columnDefinition = "jsonb")
    private List<String> studentIds;

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Column(name = "teacher_note")
    private String teacherNote;

    private String status;

    @Column(name = "assigned_by")
    private String assignedBy;

    @Column(name = "recipient_mode")
    private String recipientMode;

    private String title;

    @Column(name = "attempt_limit")
    private Integer attemptLimit;

    @Column(name = "max_duration_minutes")
    private Integer maxDurationMinutes;

    /** Explicit server-side opt-in for learner offline resume/sync. Defaults to deny. */
    @Column(name = "offline_allowed", nullable = false)
    private boolean offlineAllowed;

    @Column(name = "start_at")
    private OffsetDateTime startAt;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "recipient_count")
    private Integer recipientCount;

    /** Version used by offline assignment updates and replay acknowledgements. */
    @jakarta.persistence.Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion;
}

package com.erg.ergspringboot.lms.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Audit row behind the "Nhat ky so" tab: one edited field of a lesson-log entry; createdAt is the time of the edit. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "lms_lesson_log_changes")
public class LessonLogChange extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "entry_id", length = 36, nullable = false)
    private String entryId;

    @Column(name = "actor_id", length = 36, nullable = false)
    private String actorId;

    @Column(name = "field", length = 32, nullable = false)
    private String field;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;
}

package com.erg.ergspringboot.timekeeping.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A teacher's confirmation (or reported issue) of one month of teaching periods. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tk_teaching_confirmations")
public class TkTeachingConfirmation extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "month", length = 7, nullable = false)
    private String month;

    @Column(name = "teacher_user_id", length = 36, nullable = false)
    private String teacherUserId;

    /** PENDING | CONFIRMED | ISSUE_REPORTED. */
    @Column(name = "status", length = 16, nullable = false)
    private String status = "PENDING";

    /** Main-teacher sheet total at confirmation time. */
    @Column(name = "confirmed_total")
    private Integer confirmedTotal;

    @Column(name = "confirmed_assistant_total")
    private Integer confirmedAssistantTotal;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "issue_note", length = 1000)
    private String issueNote;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}

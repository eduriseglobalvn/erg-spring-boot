package com.erg.ergspringboot.model.calendar;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "teaching_schedule_batches")
public class CalendarBatch extends BaseEntity {

    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "school_id", length = 36, nullable = false)
    private String schoolId;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    @Column(name = "apply_from", nullable = false)
    private LocalDate applyFrom;

    @Column(name = "repeat_weeks", nullable = false)
    private Integer repeatWeeks;

    @Column(length = 20, nullable = false)
    private String status = "DRAFT";

    @Column(name = "source", length = 30, nullable = false)
    private String source = "LCMS_BULK_GRID";

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "published_by", length = 36)
    private String publishedBy;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
}

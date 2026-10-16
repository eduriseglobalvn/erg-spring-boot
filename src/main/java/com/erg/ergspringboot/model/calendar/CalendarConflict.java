package com.erg.ergspringboot.model.calendar;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Entity
@Table(name = "teaching_schedule_conflicts")
public class CalendarConflict extends BaseEntity {

    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "batch_id", length = 36)
    private String batchId;

    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "conflicting_event_id", length = 36)
    private String conflictingEventId;

    @Column(name = "conflict_type", length = 50, nullable = false)
    private String conflictType;

    @Column(length = 20, nullable = false)
    private String severity = "ERROR";

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "client_row_id", length = 100)
    private String clientRowId;

    @Column(nullable = false)
    private Boolean resolved = false;

    @Column(name = "resolved_by", length = 36)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
}

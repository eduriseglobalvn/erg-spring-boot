package com.erg.ergspringboot.model.calendar;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "teaching_schedule_events")
public class CalendarEvent extends BaseEntity {

    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "batch_id", length = 36, nullable = false)
    private String batchId;

    @Column(name = "school_id", length = 36, nullable = false)
    private String schoolId;

    @Column(name = "academic_class_id", length = 36, nullable = false)
    private String academicClassId;

    @Column(name = "subject_id", length = 36, nullable = false)
    private String subjectId;

    @Column(name = "level_id", length = 36)
    private String levelId;

    @Column(name = "room_id", length = 36)
    private String roomId;

    @Column(name = "room_name", length = 255)
    private String roomName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", insertable = false, updatable = false)
    @EqualsAndHashCode.Exclude
    private CalendarRoom room;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Column(name = "weekday", nullable = false)
    private Short weekday;

    @Column(name = "session", length = 20)
    private String session;

    @Column(name = "period_start", nullable = false, columnDefinition = "smallint default 1")
    private Short periodStart = 1;

    @Column(name = "period_count", nullable = false, columnDefinition = "smallint default 1")
    private Short periodCount = 1;

    @Column(name = "status", length = 20, nullable = false, columnDefinition = "varchar(20) default 'DRAFT'")
    private String status = "DRAFT";

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "updated_by", length = 36)
    private String updatedBy;
}

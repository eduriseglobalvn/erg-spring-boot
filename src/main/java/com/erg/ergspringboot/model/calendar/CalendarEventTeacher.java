package com.erg.ergspringboot.model.calendar;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "teaching_schedule_event_teachers")
public class CalendarEventTeacher extends BaseEntity {

    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;

    @Column(name = "teacher_user_id", length = 36, nullable = false)
    private String teacherUserId;

    @Column(length = 20, nullable = false)
    private String role;

    @Column(name = "display_name_snapshot", length = 255)
    private String displayNameSnapshot;

    @Column(name = "color_key", length = 100)
    private String colorKey;
}

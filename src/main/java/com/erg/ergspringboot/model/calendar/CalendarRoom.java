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
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Entity
@Table(name = "teaching_schedule_rooms")
public class CalendarRoom extends BaseEntity {

    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "school_id", length = 36, nullable = false)
    private String schoolId;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "status", length = 20, nullable = false, columnDefinition = "varchar(20) default 'ACTIVE'")
    private String status = "ACTIVE";

    @Column(name = "created_by", length = 36)
    private String createdBy;
}

package com.erg.ergspringboot.model.school;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "school_academic_years", indexes = {
        @Index(name = "idx_school_year_tenant_school_status", columnList = "tenant_id, school_id, status, start_year")
})
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class SchoolAcademicYear extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;
    @Column(name = "school_id", nullable = false, length = 36)
    private String schoolId;
    @Column(name = "start_year", nullable = false)
    private int startYear;
    @Column(name = "end_year", nullable = false)
    private int endYear;
    @Column(nullable = false)
    private String status;
    @Column(name = "starts_on")
    private LocalDate startsOn;
    @Column(name = "ends_on")
    private LocalDate endsOn;
    @Version
    private long version;
    @Column(name = "created_by")
    private String createdBy;
    @Column(name = "updated_by")
    private String updatedBy;
}

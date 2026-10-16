package com.erg.ergspringboot.model.users;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "employee_profiles")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class EmployeeProfile extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User user;

    @Column(name = "employee_code", nullable = false, length = 100)
    private String employeeCode;

    @Column(name = "department_id", length = 36)
    private String departmentId;

    @Column(name = "job_title", length = 150)
    private String jobTitle;

    @Column(name = "employment_type", length = 50)
    private String employmentType;

    @Column(name = "joined_at")
    private LocalDate joinedAt;

    @Column(name = "terminated_at")
    private LocalDate terminatedAt;

    @Column(name = "experience_years", precision = 5, scale = 2)
    private BigDecimal experienceYears;

    @Column(name = "experience_summary", length = 2000)
    private String experienceSummary;

    @Column(name = "manager_id", length = 36)
    private String managerId;

    @Column(name = "work_location", length = 255)
    private String workLocation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "emergency_contact", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> emergencyContact = new LinkedHashMap<>();
}

package com.erg.ergspringboot.timekeeping.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tk_policies")
public class TkPolicy extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "school_id", nullable = false)
    private String schoolId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "late_tolerance_min", nullable = false)
    private Integer lateToleranceMin = 5;

    @Column(name = "late_block_min", nullable = false)
    private Integer lateBlockMin = 15;

    @Column(name = "require_face", nullable = false)
    private Boolean requireFace = true;

    @Column(name = "allow_outside", nullable = false)
    private Boolean allowOutside = false;

    @Column(name = "explanations_per_month", nullable = false)
    private Integer explanationsPerMonth = 3;

    @Column(name = "explanation_window_days", nullable = false)
    private Integer explanationWindowDays = 3;

    @Column(name = "annual_leave_days", nullable = false)
    private Integer annualLeaveDays = 12;

    @Column(name = "seniority_step_years", nullable = false)
    private Integer seniorityStepYears = 5;

    @Column(name = "lock_day_of_month", nullable = false)
    private Integer lockDayOfMonth = 8;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "approval_chains", nullable = false, columnDefinition = "jsonb")
    private String approvalChains = "{}";
}

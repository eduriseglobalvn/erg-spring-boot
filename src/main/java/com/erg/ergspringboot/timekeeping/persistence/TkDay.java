package com.erg.ergspringboot.timekeeping.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import com.erg.ergspringboot.timekeeping.DayStatus;
import com.erg.ergspringboot.timekeeping.LeaveKind;
import com.erg.ergspringboot.timekeeping.Missing;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tk_days")
public class TkDay extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "school_id", nullable = false)
    private String schoolId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "shift_id")
    private String shiftId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DayStatus status;

    @Column(name = "work_units", nullable = false, precision = 3, scale = 1)
    private BigDecimal workUnits = BigDecimal.ZERO;

    @Column(name = "late_minutes", nullable = false)
    private Integer lateMinutes = 0;

    @Column(name = "early_minutes", nullable = false)
    private Integer earlyMinutes = 0;

    @Column(name = "deduct_minutes", nullable = false)
    private Integer deductMinutes = 0;

    @Column(name = "periods", nullable = false)
    private Integer periods = 0;

    @Column(name = "remote", nullable = false)
    private Boolean remote = false;

    @Column(name = "in_punch_id")
    private String inPunchId;

    @Column(name = "out_punch_id")
    private String outPunchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "missing")
    private Missing missing;

    /** Kind of the approved leave behind LEAVE / SICK / HALF (paid vs unpaid on the sheet); null otherwise. */
    @Enumerated(EnumType.STRING)
    @Column(name = "leave_kind")
    private LeaveKind leaveKind;
}

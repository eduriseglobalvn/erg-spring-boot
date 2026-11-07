package com.erg.ergspringboot.timekeeping.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import com.erg.ergspringboot.timekeeping.DayPart;
import com.erg.ergspringboot.timekeeping.ExplanationReason;
import com.erg.ergspringboot.timekeeping.LeaveKind;
import com.erg.ergspringboot.timekeeping.RequestStatus;
import com.erg.ergspringboot.timekeeping.RequestType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tk_requests")
public class TkRequest extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "school_id", nullable = false)
    private String schoolId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private RequestType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_kind")
    private LeaveKind leaveKind;

    @Column(name = "from_date")
    private LocalDate fromDate;

    @Column(name = "to_date")
    private LocalDate toDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "part", nullable = false)
    private DayPart part = DayPart.FULL;

    @Column(name = "days", precision = 4, scale = 1)
    private BigDecimal days;

    @Column(name = "work_date")
    private LocalDate workDate;

    @Column(name = "proposed_out")
    private LocalTime proposedOut;

    @Enumerated(EnumType.STRING)
    @Column(name = "explanation_reason")
    private ExplanationReason explanationReason;

    @Column(name = "note")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequestStatus status = RequestStatus.DRAFT;

    @Column(name = "current_step")
    private Integer currentStep;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "last_reminded_at")
    private Instant lastRemindedAt;
}

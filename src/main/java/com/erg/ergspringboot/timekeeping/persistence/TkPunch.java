package com.erg.ergspringboot.timekeeping.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import com.erg.ergspringboot.timekeeping.PunchDirection;
import com.erg.ergspringboot.timekeeping.PunchStatus;
import com.erg.ergspringboot.timekeeping.ReviewDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tk_punches")
public class TkPunch extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "school_id", nullable = false)
    private String schoolId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "site_id")
    private String siteId;

    @Column(name = "shift_id")
    private String shiftId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private PunchDirection direction;

    @Column(name = "server_time", nullable = false)
    private Instant serverTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PunchStatus status;

    @Column(name = "method")
    private String method;

    @Column(name = "remote", nullable = false)
    private Boolean remote = false;

    @Column(name = "mock_reported", nullable = false)
    private Boolean mockReported = false;

    @Column(name = "lat", precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "lng", precision = 9, scale = 6)
    private BigDecimal lng;

    @Column(name = "accuracy_m")
    private Integer accuracyM;

    @Column(name = "distance_m")
    private Integer distanceM;

    @Column(name = "reason")
    private String reason;

    @Column(name = "photo_key")
    private String photoKey;

    @Column(name = "photo_purged_at")
    private Instant photoPurgedAt;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_decision")
    private ReviewDecision reviewDecision;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note")
    private String reviewNote;
}

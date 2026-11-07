package com.erg.ergspringboot.timekeeping.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import com.erg.ergspringboot.timekeeping.StepStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tk_request_steps")
public class TkRequestStep extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "step_no", nullable = false)
    private Integer stepNo;

    @Column(name = "resolver", nullable = false)
    private String resolver;

    @Column(name = "approver_id")
    private String approverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StepStatus status = StepStatus.WAITING;

    @Column(name = "acted_by")
    private String actedBy;

    @Column(name = "acted_at")
    private Instant actedAt;

    @Column(name = "on_behalf", nullable = false)
    private Boolean onBehalf = false;

    @Column(name = "comment")
    private String comment;

    @Column(name = "viewed_at")
    private Instant viewedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "due_at")
    private Instant dueAt;
}

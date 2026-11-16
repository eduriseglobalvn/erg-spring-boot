package com.erg.ergspringboot.timekeeping.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Close state of one teaching-hours month (yyyy-MM) for a tenant. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tk_teaching_months")
public class TkTeachingMonth extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "month", length = 7, nullable = false)
    private String month;

    /** COLLECTING | READY | CLOSED. */
    @Column(name = "status", length = 16, nullable = false)
    private String status = "COLLECTING";

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by", length = 36)
    private String closedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}

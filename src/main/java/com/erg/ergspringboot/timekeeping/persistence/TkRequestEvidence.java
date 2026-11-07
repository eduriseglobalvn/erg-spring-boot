package com.erg.ergspringboot.timekeeping.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tk_request_evidence")
public class TkRequestEvidence extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "kind", nullable = false)
    private String kind = "SCHEDULE";

    @Column(name = "ref", nullable = false)
    private String ref;

    @Column(name = "label")
    private String label;
}

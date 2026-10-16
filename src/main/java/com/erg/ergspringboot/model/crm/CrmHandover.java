package com.erg.ergspringboot.model.crm;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "crm_handovers", indexes = {
        @Index(name = "idx_crm_handovers_tenant_school", columnList = "tenant_id, school_id"),
        @Index(name = "idx_crm_handovers_opportunity", columnList = "opportunity_id")
})
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class CrmHandover extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "school_id", nullable = false, length = 36)
    private String schoolId;

    @Column(name = "lead_id", nullable = false, length = 36)
    private String leadId;

    @Column(name = "opportunity_id", nullable = false, length = 36)
    private String opportunityId;

    @Column(name = "requested_by", nullable = false, length = 36)
    private String requestedBy;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checklist", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> checklist = new ArrayList<>();

    @Column(name = "idempotency_key", length = 192)
    private String idempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}

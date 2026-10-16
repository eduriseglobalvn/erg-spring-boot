package com.erg.ergspringboot.model.crm;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "crm_school_deals",
        indexes = {
                @Index(name = "idx_crm_school_deals_school", columnList = "school_id"),
                @Index(name = "idx_crm_school_deals_owner_stage", columnList = "owner_user_id, stage"),
                @Index(name = "idx_crm_school_deals_created", columnList = "owner_user_id, created_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class CrmSchoolDeal extends BaseEntity {

    @Column(name = "school_id", length = 36, nullable = false)
    private String schoolId;

    @Column(name = "owner_user_id", length = 36, nullable = false)
    private String ownerUserId;

    @Column(name = "stage")
    private String stage;

    @Column(name = "expected_revenue")
    private BigDecimal expectedRevenue;

    @Column(name = "signed_revenue")
    private BigDecimal signedRevenue;

    @Column(name = "probability")
    private BigDecimal probability;

    @Column(name = "lost_reason")
    private String lostReason;

    @Column(name = "lost_note", length = 1000)
    private String lostNote;

    @Column(name = "signed_at")
    private OffsetDateTime signedAt;

    @Column(name = "lost_at")
    private OffsetDateTime lostAt;

    @Column(length = 100)
    private String code;

    @Column(name = "academic_year", length = 32)
    private String academicYear;

    @Column(name = "contract_term", length = 100)
    private String contractTerm;

    @Column(name = "start_month", length = 32)
    private String startMonth;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "revenue_lines", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> revenueLines = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cost_lines", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> costLines = new ArrayList<>();

    @Column(name = "idempotency_key", length = 192)
    private String idempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}

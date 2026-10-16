package com.erg.ergspringboot.model.security;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.erg.ergspringboot.model.BaseEntity;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go relational core models.
 */
@Entity
@Table(name = "firewall_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class FirewallRule extends BaseEntity {

    private String entry;

    private String ruleType;

    private String reason;

    private String source;

    private Boolean active;

    private OffsetDateTime expiresAt;

    private OffsetDateTime revokedAt;

    private String revokedBy;

    private String createdBy;
}

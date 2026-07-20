package com.erg.ergspringboot.model.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.erg.ergspringboot.model.BaseEntity;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Migrated from erg-go relational core models.
 */
@Entity
@Table(
        name = "education_units",
        indexes = {
                @Index(name = "idx_education_units_parent", columnList = "parent_id"),
                @Index(name = "idx_education_units_type_status", columnList = "type, status"),
                @Index(name = "idx_education_units_name", columnList = "name")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EducationUnit extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId = "erg";

    private String name;

    private String slug;

    private String type;


    @Column(name = "parent_id", length = 36, nullable = true)
    private String parentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", referencedColumnName = "id", nullable = true, insertable = false, updatable = false)
    private EducationUnit parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<EducationUnit> children = new LinkedHashSet<>();

    private String logoUrl;

    private String description;

    private String status;

    @Column(name = "owner_user_id")
    private String ownerUserId;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "source")
    private String source;

    @Column(name = "signed_at")
    private OffsetDateTime signedAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}

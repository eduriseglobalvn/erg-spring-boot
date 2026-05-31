package com.erg.ergspringboot.model.hoclieu;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Migrated from erg-go internal/modules/hoclieu/domain/entity/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "hoclieu_taxonomy",
        indexes = {
                @Index(name = "idx_hoclieu_taxonomy_tenant_kind_status", columnList = "tenant_id, kind, status"),
                @Index(name = "idx_hoclieu_taxonomy_subject_parent", columnList = "subject_id, parent_id"),
                @Index(name = "idx_hoclieu_taxonomy_category", columnList = "category_id")
        }
)
@AttributeOverride(name = "id", column = @Column(name = "id", length = 96, nullable = false, updatable = false))
public class TaxonomyNode extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String label;

    private String slug;

    @Column(name = "parent_id", length = 96)
    private String parentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", referencedColumnName = "id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TaxonomyNode parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<TaxonomyNode> children = new LinkedHashSet<>();

    @Column(name = "subject_id", length = 96)
    private String subjectId;

    @Column(name = "grade_id", length = 96)
    private String gradeId;

    @Column(name = "category_id", length = 96)
    private String categoryId;

    @Column(name = "book_series_id", length = 96)
    private String bookSeriesId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "level_ids", columnDefinition = "jsonb")
    private List<String> levelIds;

    @Column(name = "depth")
    private Integer depth;

    @Column(name = "sort_order")
    private Integer sortOrder;

    private String status;

    private String description;
}

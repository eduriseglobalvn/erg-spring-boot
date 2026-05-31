package com.erg.ergspringboot.model.hoclieu;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        name = "hoclieu_resources",
        indexes = {
                @Index(name = "idx_hoclieu_resources_tenant_subject", columnList = "tenant_id, subject_id"),
                @Index(name = "idx_hoclieu_resources_tenant_category", columnList = "tenant_id, category_id"),
                @Index(name = "idx_hoclieu_resources_tenant_section", columnList = "tenant_id, section_id"),
                @Index(name = "idx_hoclieu_resources_tenant_topic", columnList = "tenant_id, topic_id"),
                @Index(name = "idx_hoclieu_resources_status_visibility", columnList = "tenant_id, status, visibility")
        }
)
@AttributeOverride(name = "id", column = @Column(name = "id", length = 96, nullable = false, updatable = false))
public class ResourceRecord extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "program_slug")
    private String programSlug;

    @Column(name = "subject_id", length = 96, nullable = false)
    private String subjectId;

    @Column(name = "grade_id", length = 96)
    private String gradeId;

    @Column(name = "category_id", length = 96)
    private String categoryId;

    @Column(name = "section_id", length = 96)
    private String sectionId;

    @Column(name = "book_series_id", length = 96)
    private String bookSeriesId;

    @Column(name = "topic_id", length = 96)
    private String topicId;

    @Column(name = "level_id", length = 96)
    private String levelId;

    @Column(name = "document_type_id", length = 96)
    private String documentTypeId;

    @Column(nullable = false)
    private String title;

    private String slug;

    private String subtitle;

    private String description;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> metadata;

    private String visibility;

    private String status;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lecture_design", columnDefinition = "jsonb")
    private Map<String, Object> lectureDesign;

    @Column(name = "created_by", length = 96)
    private String createdBy;

    @Column(name = "updated_by", length = 96)
    private String updatedBy;

    @OneToMany(mappedBy = "resource", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<AssetRecord> assets = new LinkedHashSet<>();

    @OneToMany(mappedBy = "resource", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<ResourceItemRecord> items = new LinkedHashSet<>();
}

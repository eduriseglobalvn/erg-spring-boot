package com.erg.ergspringboot.model.hoclieu;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "hoclieu_slide_annotations",
        indexes = @Index(name = "idx_hoclieu_annotations_asset_slide", columnList = "tenant_id, asset_id, slide_index"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_hoclieu_annotation_owner_scope",
                columnNames = {"tenant_id", "user_id", "school_id", "academic_year", "asset_id", "slide_index"}
        )
)
@AttributeOverride(name = "id", column = @Column(name = "id", length = 96, nullable = false, updatable = false))
public class HocLieuSlideAnnotation extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "user_id", length = 96, nullable = false)
    private String userId;

    @Column(name = "school_id", length = 96, nullable = false)
    private String schoolId;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(name = "asset_id", length = 96, nullable = false)
    private String assetId;

    @Column(name = "slide_index", nullable = false)
    private Integer slideIndex;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> paths;
}

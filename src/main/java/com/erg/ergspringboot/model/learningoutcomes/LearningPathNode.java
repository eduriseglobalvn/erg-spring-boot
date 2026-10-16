package com.erg.ergspringboot.model.learningoutcomes;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "learning_path_nodes", indexes = {
        @Index(name = "idx_learning_path_nodes_tenant_path", columnList = "tenant_id, path_id, sort_order")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class LearningPathNode extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 96)
    private String tenantId;

    @Column(name = "path_id", nullable = false, length = 96)
    private String pathId;

    @Column(name = "content_ref_id", nullable = false, length = 96)
    private String contentRefId;

    @Column(name = "content_kind", nullable = false, length = 48)
    private String contentKind;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "required", nullable = false)
    private boolean required;
}

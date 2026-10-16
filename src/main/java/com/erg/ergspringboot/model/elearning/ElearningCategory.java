package com.erg.ergspringboot.model.elearning;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/elearning/domain/entity/category.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"createdAt", "updatedAt"})
public class ElearningCategory {

    private String id;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;

    private String tenantId;

    private String name;

    private String slug;

    private String description;

    private String parentId;

    private Integer order;

    private Boolean isActive;

    private List<ElearningLevelRef> levels;
}

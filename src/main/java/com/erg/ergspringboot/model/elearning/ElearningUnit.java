package com.erg.ergspringboot.model.elearning;

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
public class ElearningUnit {

    private String id;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;

    private String tenantId;

    private String levelId;

    private String name;

    private String slug;

    private String description;

    private String content;

    private Integer order;
}

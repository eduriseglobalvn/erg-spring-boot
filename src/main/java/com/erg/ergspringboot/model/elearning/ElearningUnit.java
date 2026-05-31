package com.erg.ergspringboot.model.elearning;

import com.erg.ergspringboot.model.BaseEntity;
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
@EqualsAndHashCode(callSuper = true)
public class ElearningUnit extends BaseEntity {

    private String tenantId;

    private String levelId;

    private String name;

    private String slug;

    private String description;

    private String content;

    private Integer order;
}

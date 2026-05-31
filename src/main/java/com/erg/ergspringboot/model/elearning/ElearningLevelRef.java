package com.erg.ergspringboot.model.elearning;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/elearning/domain/entity/category.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ElearningLevelRef {

    private String levelId;

    private String name;
}

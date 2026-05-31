package com.erg.ergspringboot.model.documents;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/documents/domain/entity/document.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatermarkConfig {

    private String text;

    private String position;

    private Double opacity;

    private String color;

    private Integer fontSize;

    private Boolean perPage;

    private Double offsetX;

    private Double offsetY;

    private Double rotation;
}

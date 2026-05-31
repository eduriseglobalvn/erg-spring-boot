package com.erg.ergspringboot.model.seo;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/seo/domain/entity/entity.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeoConfig {

    private String key;

    private Object value;

    private String updatedBy;

    private OffsetDateTime updatedAt;
}

package com.erg.ergspringboot.model.seo;

import com.erg.ergspringboot.model.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/seo/domain/entity/entity.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SeoRedirect extends BaseEntity {

    private String fromPattern;

    private String toUrl;

    private String type;

    private Boolean isRegex;

    private Boolean isActive;

    private Integer hitCount;
}

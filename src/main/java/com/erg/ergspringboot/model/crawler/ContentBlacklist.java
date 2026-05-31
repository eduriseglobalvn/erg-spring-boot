package com.erg.ergspringboot.model.crawler;

import com.erg.ergspringboot.model.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/crawler/domain/entity/crawler.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ContentBlacklist extends BaseEntity {

    private String type;

    private String pattern;

    private String reason;

    private Integer blockedCount;

    private Boolean enabled;

    private String createdBy;
}

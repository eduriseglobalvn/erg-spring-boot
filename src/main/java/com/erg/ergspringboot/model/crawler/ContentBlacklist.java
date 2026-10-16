package com.erg.ergspringboot.model.crawler;

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
@EqualsAndHashCode(exclude = {"createdAt", "updatedAt"})
public class ContentBlacklist {

    private String id;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;

    private String type;

    private String pattern;

    private String reason;

    private Integer blockedCount;

    private Boolean enabled;

    private String createdBy;
}

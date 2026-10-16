package com.erg.ergspringboot.model.crawler;

import java.time.OffsetDateTime;
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
public class RSSFeed {

    private String id;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;

    private String name;

    private String url;

    private String type;

    private String category;

    private String language;

    private String frequency;

    private Boolean enabled;

    private OffsetDateTime lastFetchAt;

    private OffsetDateTime lastItemAt;

    private Integer itemCount;

    private Integer errorCount;

    private String lastError;

    private String eTag;

    private String lastModified;
}

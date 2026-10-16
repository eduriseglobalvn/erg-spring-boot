package com.erg.ergspringboot.model.crawler;

import java.time.OffsetDateTime;
import java.util.List;
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
public class CrawlHistory {

    private String id;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;

    private String url;

    private String feedId;

    private String jobId;

    private String status;

    private Integer hTTPStatus;

    private Long responseSize;

    private Long durationMS;

    private String errorCode;

    private String errorMsg;

    private Double qualityScore;

    private Integer step;

    private String title;

    private String description;

    private String contentHash;

    private List<String> tags;

    private String language;

    private OffsetDateTime crawledAt;
}

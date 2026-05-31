package com.erg.ergspringboot.model.crawler;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/crawler/domain/entity/crawler.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrawlJobPayload {

    private String url;

    private String feedId;

    private String jobId;

    private Integer priority;

    private Integer depth;

    private String configId;

    private String userId;
}

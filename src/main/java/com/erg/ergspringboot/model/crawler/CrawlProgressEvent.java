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
public class CrawlProgressEvent {

    private String jobId;

    private Integer step;

    private Integer total;

    private String message;

    private Boolean done;

    private String error;
}

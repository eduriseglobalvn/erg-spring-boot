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
public class JobStatusResponse {

    private String jobId;

    private String url;

    private String status;

    private Integer step;

    private String message;

    private String error;
}

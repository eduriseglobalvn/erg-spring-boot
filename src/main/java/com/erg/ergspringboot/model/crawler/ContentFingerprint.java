package com.erg.ergspringboot.model.crawler;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/crawler/domain/entity/crawler.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentFingerprint {

    private String id;

    private String url;

    private Long simHash;

    private String sHA256;

    private Integer bucket;

    private String crawlId;

    private OffsetDateTime crawledAt;
}

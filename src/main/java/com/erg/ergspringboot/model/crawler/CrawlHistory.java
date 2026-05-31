package com.erg.ergspringboot.model.crawler;

import com.erg.ergspringboot.model.BaseEntity;
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
@EqualsAndHashCode(callSuper = true)
public class CrawlHistory extends BaseEntity {

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

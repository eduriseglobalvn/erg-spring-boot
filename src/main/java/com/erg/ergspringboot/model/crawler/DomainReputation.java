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
public class DomainReputation {

    private String id;

    private String domain;

    private Integer blockCount;

    private Integer successCount;

    private Integer failCount;

    private Long avgDuration;

    private Double score;

    private OffsetDateTime lastSeenAt;

    private OffsetDateTime updatedAt;
}

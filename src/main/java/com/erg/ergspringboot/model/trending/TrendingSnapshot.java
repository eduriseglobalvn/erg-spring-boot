package com.erg.ergspringboot.model.trending;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/trending/domain/entity/trending_snapshot.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendingSnapshot {

    private String id;

    private List<String> topics;

    private Integer topicCount;

    private OffsetDateTime generatedAt;

    private OffsetDateTime createdAt;
}

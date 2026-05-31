package com.erg.ergspringboot.model.trending;

import com.erg.ergspringboot.model.BaseEntity;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/trending/domain/entity/trending_topic.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TrendingTopic extends BaseEntity {

    private String topic;

    private String slug;

    private Double score;

    private Integer volume;

    private String source;

    private List<String> keywords;

    private List<String> urls;

    private List<Integer> timeline;

    private OffsetDateTime lastRefreshedAt;
}

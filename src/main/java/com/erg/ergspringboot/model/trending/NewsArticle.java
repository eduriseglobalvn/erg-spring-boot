package com.erg.ergspringboot.model.trending;

import com.erg.ergspringboot.model.BaseEntity;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/trending/domain/entity/news_article.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NewsArticle extends BaseEntity {

    private String topic;

    private String headline;

    private String source;

    private String url;

    private OffsetDateTime publishedAt;

    private Double relevanceScore;
}

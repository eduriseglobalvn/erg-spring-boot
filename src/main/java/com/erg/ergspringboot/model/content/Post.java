package com.erg.ergspringboot.model.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.erg.ergspringboot.model.BaseEntity;
import com.erg.ergspringboot.model.users.User;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go relational core models.
 */
@Entity
@Table(name = "posts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Post extends BaseEntity {

    private String title;

    private String slug;

    private String excerpt;

    private String content;

    private String metaJson;

    private String thumbnailUrl;

    private String status;

    private Boolean isPublished;

    private OffsetDateTime publishedAt;

    @Column(name = "created_by_id", length = 36)
    private String createdById;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User createdBy;

    @Column(name = "published_by_id", length = 36)
    private String publishedById;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User publishedBy;

    @Column(name = "author_id", length = 36)
    private String authorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User author;

    private Long viewCount;

    private Long commentCount;


    @Column(name = "category_id", length = 36, nullable = false)
    private String categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
    private PostCategory category;

    private Boolean isCreatedByAi;

    private String aiprompt;

    private String aiJobId;

    private String metaTitle;

    private String metaDescription;

    private String focusKeyword;

    private String keywords;

    private String canonicalUrl;

    private String schemaType;

    private Integer seoScore;

    private Integer readabilityScore;

    private Double keywordDensity;

    private String schemaMarkupJson;

    private String schemaDataJson;

    private Boolean robotsIndex;

    private Boolean robotsFollow;

    private String robotsAdvanced;

    private String ogTitle;

    private String ogDescription;

    private String ogImage;

    private String twitterCard;

    private String breadcrumbTitle;

    private String faqItemsJson;

    private String howToStepsJson;

    private String introVideoJson;

    private String tagsJson;

    private OffsetDateTime deletedAt;
}

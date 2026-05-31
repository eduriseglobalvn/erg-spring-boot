package com.erg.ergspringboot.model.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go relational core models.
 */
@Entity
@Table(name = "community_media")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommunityMedia {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;


    @Column(name = "post_id", length = 36, nullable = false)
    private String postId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
    private CommunityPost post;

    private String mediaType;

    private String url;

    private String storageKey;

    private String thumbnailUrl;

    private String originalName;

    private String mimeType;

    private Long sizeBytes;

    private Integer width;

    private Integer height;

    private Integer durationSec;

    private Integer sortOrder;

    private OffsetDateTime createdAt;
}

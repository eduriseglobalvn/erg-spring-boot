package com.erg.ergspringboot.model.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.erg.ergspringboot.model.BaseEntity;
import com.erg.ergspringboot.model.users.User;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Migrated from erg-go relational core models.
 */
@Entity
@Table(name = "community_posts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class CommunityPost extends BaseEntity {

    private String tenantId;


    @Column(name = "topic_id", length = 36, nullable = false)
    private String topicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
    private CommunityTopic topic;


    @Column(name = "author_id", length = 36, nullable = false)
    private String authorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
    private User author;

    private String title;

    private String content;

    private String postType;

    private String status;

    private String visibility;

    private String tagsJson;

    private Boolean isPinned;

    private Boolean isLocked;

    private Long viewCount;

    private Long commentCount;

    private Long reactionCount;

    private Long shareCount;

    private OffsetDateTime lastActivityAt;

    private OffsetDateTime deletedAt;

    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<CommunityMedia> media = new LinkedHashSet<>();

    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<CommunityComment> comments = new LinkedHashSet<>();
}

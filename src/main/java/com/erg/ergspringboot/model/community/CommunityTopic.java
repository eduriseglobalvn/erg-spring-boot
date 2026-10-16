package com.erg.ergspringboot.model.community;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.erg.ergspringboot.model.BaseEntity;
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
@Table(name = "community_topics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class CommunityTopic extends BaseEntity {

    private String tenantId;

    private String slug;

    private String name;

    private String description;

    private String groupName;

    private String icon;

    private String color;

    private Integer sortOrder;

    private Boolean isFeatured;

    private Long threadCount;

    private Long postCount;

    private Long followerCount;

    private String lastPostId;

    private OffsetDateTime lastPostAt;

    private String createdBy;

    private OffsetDateTime deletedAt;

    @OneToMany(mappedBy = "topic", fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<CommunityPost> posts = new LinkedHashSet<>();
}

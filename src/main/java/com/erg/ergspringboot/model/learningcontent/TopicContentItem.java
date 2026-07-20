package com.erg.ergspringboot.model.learningcontent;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "topic_content_items",
        indexes = {
                @Index(name = "idx_topic_content_items_topic_sort", columnList = "tenant_id, topic_id, status, sort_order"),
                @Index(name = "idx_topic_content_items_content_item", columnList = "tenant_id, content_item_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_topic_content_items_item",
                        columnNames = {"tenant_id", "topic_id", "content_item_id", "content_role"})
        }
)
@AttributeOverride(name = "id", column = @Column(name = "id", length = 96, nullable = false, updatable = false))
public class TopicContentItem extends BaseEntity {

    @Column(name = "tenant_id", length = 96, nullable = false)
    private String tenantId;

    @Column(name = "topic_id", length = 96, nullable = false)
    private String topicId;

    @Column(name = "content_item_id", length = 96, nullable = false)
    private String contentItemId;

    @Column(name = "content_role", length = 64, nullable = false)
    private String contentRole;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_by", length = 96)
    private String createdBy;
}

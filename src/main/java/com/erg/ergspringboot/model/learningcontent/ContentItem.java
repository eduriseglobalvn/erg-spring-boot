package com.erg.ergspringboot.model.learningcontent;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Entity
@Table(
        name = "content_items",
        indexes = {
                @Index(name = "idx_content_items_tenant_status_sort", columnList = "tenant_id, status, updated_at"),
                @Index(name = "idx_content_items_tenant_type_status", columnList = "tenant_id, content_type, status"),
                @Index(name = "idx_content_items_tenant_owner", columnList = "tenant_id, owner_id")
        }
)
@AttributeOverride(name = "id", column = @Column(name = "id", length = 96, nullable = false, updatable = false))
public class ContentItem extends BaseEntity {

    @Column(name = "tenant_id", length = 96, nullable = false)
    private String tenantId;

    @Column(name = "content_type", length = 64, nullable = false)
    private String contentType;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "lifecycle_state", nullable = false, length = 32)
    private String lifecycleState = "DRAFT";

    @Column(nullable = false, length = 32)
    private String visibility;

    @Column(name = "owner_id", length = 96)
    private String ownerId;

    @Column(name = "created_by", length = 96)
    private String createdBy;

    @Column(name = "updated_by", length = 96)
    private String updatedBy;

    @jakarta.persistence.Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion = 0L;
}

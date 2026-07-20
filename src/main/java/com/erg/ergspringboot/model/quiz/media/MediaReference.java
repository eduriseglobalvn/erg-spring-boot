package com.erg.ergspringboot.model.quiz.media;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Reference from an owner (draft slide, quiz version, bank question) to a {@link MediaAsset} (spec §1.6).
 * One row per (media, owner) so ref-counting stays idempotent under retries; drives orphan GC in Phase 6.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "media_references",
        indexes = {
                @Index(name = "idx_media_references_owner", columnList = "tenant_id, owner_type, owner_id")
        }
)
public class MediaReference extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "media_id", nullable = false)
    private String mediaId;

    @Column(name = "owner_type", nullable = false)
    private String ownerType;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;
}

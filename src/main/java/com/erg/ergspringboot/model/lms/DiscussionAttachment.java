package com.erg.ergspringboot.model.lms;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/lms/application/service/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscussionAttachment {

    private String id;

    private String tenantId;

    private String url;

    private String mime;

    private Long size;

    private String ownerId;

    private OffsetDateTime createdAt;
}

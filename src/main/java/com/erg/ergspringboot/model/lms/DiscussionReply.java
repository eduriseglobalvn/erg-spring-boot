package com.erg.ergspringboot.model.lms;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/lms/application/service/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscussionReply {

    private String id;

    private String tenantId;

    private String threadId;

    private String academicClassId;

    private String content;

    private List<String> attachmentIds;

    private String authorId;

    private OffsetDateTime createdAt;
}

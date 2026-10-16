package com.erg.ergspringboot.model.lms;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/lms/application/service/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"createdAt", "updatedAt"})
public class DiscussionThread {

    private String id;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;

    private String tenantId;

    private String academicClassId;

    private String title;

    private String content;

    private String assignmentId;

    private List<String> attachmentIds;

    private String authorId;

    private Integer replyCount;

    private OffsetDateTime latestActivityAt;
}

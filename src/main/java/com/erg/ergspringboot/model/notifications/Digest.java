package com.erg.ergspringboot.model.notifications;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/notifications/domain/entity/notification.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"createdAt", "updatedAt"})
public class Digest {

    private String id;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;

    private String userId;

    private String channel;

    private String recipient;

    private String frequency;

    private String subject;

    private List<String> items;

    private Integer count;

    private String status;

    private OffsetDateTime sentAt;
}

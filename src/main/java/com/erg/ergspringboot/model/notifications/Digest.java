package com.erg.ergspringboot.model.notifications;

import com.erg.ergspringboot.model.BaseEntity;
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
@EqualsAndHashCode(callSuper = true)
public class Digest extends BaseEntity {

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

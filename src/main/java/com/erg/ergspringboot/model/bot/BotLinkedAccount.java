package com.erg.ergspringboot.model.bot;

import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/bot/domain/entity/bot_linked_account.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BotLinkedAccount {

    private String id;

    private String platform;

    private String platformUserId;

    private String internalUserId;

    private String displayName;

    private String username;

    private String avatarUrl;

    private String linkCode;

    private OffsetDateTime verifiedAt;

    private OffsetDateTime linkedAt;

    private OffsetDateTime unlinkedAt;

    private Map<String, String> metadata;
}

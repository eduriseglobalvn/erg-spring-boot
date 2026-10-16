package com.erg.ergspringboot.model.bot;

import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/bot/domain/entity/bot_conversation.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"createdAt", "updatedAt"})
public class BotConversation {

    private String id;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;

    private String userId;

    private String platform;

    private String platformConvId;

    private String state;

    private String wizardStep;

    private Map<String, String> wizardData;

    private Map<String, Object> context;

    private String lastMessageId;

    private String lastCommand;

    private Integer permissionLevel;

    private Map<String, String> metadata;

    private OffsetDateTime expiresAt;
}

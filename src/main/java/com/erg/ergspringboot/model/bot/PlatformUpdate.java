package com.erg.ergspringboot.model.bot;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/bot/domain/entity/bot_command.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformUpdate {

    private String platform;

    private String userId;

    private String username;

    private String displayName;

    private String conversationId;

    private String messageId;

    private String command;

    private List<String> args;

    private String rawText;

    private Boolean isCommand;

    private Boolean isCallback;

    private String callbackData;

    private String replyToId;

    private OffsetDateTime timestamp;

    private Map<String, String> metadata;
}

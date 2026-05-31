package com.erg.ergspringboot.model.bot;

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
public class CommandRegistry {

    private Map<String, CommandEntry> entries;

    private Map<String, String> aliases;

    private Map<String, List<CommandEntry>> byCategory;
}

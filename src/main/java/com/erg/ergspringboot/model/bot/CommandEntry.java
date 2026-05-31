package com.erg.ergspringboot.model.bot;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/bot/domain/entity/bot_command.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommandEntry {

    private String name;

    private List<String> aliases;

    private String description;

    private String usage;

    private String category;

    private String permission;

    private Boolean hidden;

    private Object cooldown;
}

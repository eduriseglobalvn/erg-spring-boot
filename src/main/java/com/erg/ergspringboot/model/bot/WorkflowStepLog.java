package com.erg.ergspringboot.model.bot;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/bot/domain/entity/bot_workflow.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStepLog {

    private String step;

    private String status;

    private String input;

    private String output;

    private String error;

    private Object duration;

    private OffsetDateTime executedAt;
}

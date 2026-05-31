package com.erg.ergspringboot.model.bot;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/bot/domain/entity/bot_workflow.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStep {

    private String name;

    private String label;

    private String type;

    private String command;

    private String handler;

    private String prompt;

    private String onSuccess;

    private String onFail;

    private String onTimeout;

    private String onComplete;

    private Integer timeoutSec;

    private Map<String, String> metadata;
}

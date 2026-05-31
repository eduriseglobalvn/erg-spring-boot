package com.erg.ergspringboot.model.bot;

import java.time.OffsetDateTime;
import java.util.List;
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
public class WorkflowExecution {

    private String id;

    private String workflowId;

    private String convId;

    private String userId;

    private String status;

    private String currentStep;

    private List<WorkflowStep> steps;

    private List<WorkflowStepLog> stepHistory;

    private Map<String, Object> data;

    private Integer retryCount;

    private Integer maxRetries;

    private String error;

    private OffsetDateTime startedAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime completedAt;
}

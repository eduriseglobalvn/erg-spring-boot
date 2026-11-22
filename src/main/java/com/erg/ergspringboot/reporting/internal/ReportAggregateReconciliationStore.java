package com.erg.ergspringboot.reporting.internal;

import java.time.OffsetDateTime;
import java.util.List;

public interface ReportAggregateReconciliationStore {
    List<Task> claim(int limit);
    void reconcile(Task task);

    /** Backs the task off exponentially, or marks it 'failed' once claim() has counted {@code maxAttempts} attempts. */
    void recordFailure(Task task, String error, int maxAttempts);

    record Task(String tenantId, String aggregateId, OffsetDateTime sourceUpdatedAt) {}
}

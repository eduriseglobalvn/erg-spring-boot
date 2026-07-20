package com.erg.ergspringboot.service.reporting;

import java.time.OffsetDateTime;
import java.util.List;

public interface ReportAggregateReconciliationStore {
    List<Task> claim(int limit);
    void reconcile(Task task);

    record Task(String tenantId, String aggregateId, OffsetDateTime sourceUpdatedAt) {}
}

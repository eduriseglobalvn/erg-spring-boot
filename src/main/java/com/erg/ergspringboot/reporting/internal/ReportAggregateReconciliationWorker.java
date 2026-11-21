package com.erg.ergspringboot.reporting.internal;

import com.erg.ergspringboot.platform.security.TenantContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(prefix = "erg.reporting.reconciliation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReportAggregateReconciliationWorker {
    private static final int BATCH_SIZE = 10;
    private static final int MAX_ATTEMPTS = 8;
    private static final Logger log = LoggerFactory.getLogger(ReportAggregateReconciliationWorker.class);
    private final ReportAggregateReconciliationStore store;
    private final TransactionTemplate transaction;

    public ReportAggregateReconciliationWorker(
            ReportAggregateReconciliationStore store, PlatformTransactionManager transactionManager) {
        this.store = store;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${erg.reporting.reconciliation.fixed-delay-ms:5000}")
    public void scheduledRun() {
        runDueBatch();
    }

    public int runDueBatch() {
        TenantContext previous = TenantContext.current();
        try {
            TenantContext.set(TenantContext.maintenance());
            List<ReportAggregateReconciliationStore.Task> tasks =
                    transaction.execute(status -> store.claim(BATCH_SIZE));
            if (tasks == null) return 0;
            for (ReportAggregateReconciliationStore.Task task : tasks) {
                TenantContext.set(TenantContext.of(task.tenantId(), "report-reconciler", "worker"));
                try {
                    transaction.executeWithoutResult(status -> store.reconcile(task));
                } catch (RuntimeException failure) {
                    recordFailure(task, failure);
                }
            }
            return tasks.size();
        } finally {
            if (previous == null) TenantContext.clear(); else TenantContext.set(previous);
        }
    }

    /** One poison task must not stop the batch or be re-claimed every few seconds forever. */
    private void recordFailure(ReportAggregateReconciliationStore.Task task, RuntimeException failure) {
        log.warn("Report aggregate reconciliation failed for tenant {} aggregate {}", task.tenantId(), task.aggregateId(), failure);
        String message = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        try {
            transaction.executeWithoutResult(status ->
                    store.recordFailure(task, message.length() <= 500 ? message : message.substring(0, 500), MAX_ATTEMPTS));
        } catch (RuntimeException recordingFailure) {
            log.error("Could not record reconciliation failure; the lease expiry will retry it", recordingFailure);
        }
    }
}

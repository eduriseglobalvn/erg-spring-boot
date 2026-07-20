package com.erg.ergspringboot.service.reporting;

import com.erg.ergspringboot.platform.security.TenantContext;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(prefix = "erg.reporting.reconciliation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReportAggregateReconciliationWorker {
    private static final int BATCH_SIZE = 10;
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
                transaction.executeWithoutResult(status -> store.reconcile(task));
            }
            return tasks.size();
        } finally {
            if (previous == null) TenantContext.clear(); else TenantContext.set(previous);
        }
    }
}

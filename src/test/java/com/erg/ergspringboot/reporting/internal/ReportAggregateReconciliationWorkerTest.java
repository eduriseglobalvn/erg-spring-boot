package com.erg.ergspringboot.reporting.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.platform.security.TenantContext;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class ReportAggregateReconciliationWorkerTest {
    @AfterEach void clear() { TenantContext.clear(); }

    @Test
    void boundedWorkerReconcilesEachTaskUnderItsTenantAndClearsContext() {
        List<String> observedTenants = new ArrayList<>();
        ReportAggregateReconciliationStore store = new ReportAggregateReconciliationStore() {
            public List<Task> claim(int limit) {
                assertThat(limit).isEqualTo(10);
                assertThat(TenantContext.current().isMaintenance()).isTrue();
                return List.of(
                        new Task("tenant-a", "assignment-a", OffsetDateTime.now()),
                        new Task("tenant-b", "assignment-b", OffsetDateTime.now()));
            }
            public void reconcile(Task task) {
                observedTenants.add(TenantContext.current().tenantId());
            }
            public void recordFailure(Task task, String error, int maxAttempts) { }
        };

        int processed = new ReportAggregateReconciliationWorker(store, new ImmediateTransactionManager())
                .runDueBatch();

        assertThat(processed).isEqualTo(2);
        assertThat(observedTenants).containsExactly("tenant-a", "tenant-b");
        assertThat(TenantContext.current()).isNull();
    }

    @Test
    void poisonTaskIsRecordedAndDoesNotStopTheRestOfTheBatch() {
        List<String> reconciled = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        ReportAggregateReconciliationStore store = new ReportAggregateReconciliationStore() {
            public List<Task> claim(int limit) {
                return List.of(new Task("tenant-a", "poison", OffsetDateTime.now()), new Task("tenant-b", "ok", OffsetDateTime.now()));
            }
            public void reconcile(Task task) {
                if (task.aggregateId().equals("poison")) throw new IllegalStateException("bad row");
                reconciled.add(task.aggregateId());
            }
            public void recordFailure(Task task, String error, int maxAttempts) {
                failed.add(task.aggregateId() + "|" + error + "|" + maxAttempts);
            }
        };

        new ReportAggregateReconciliationWorker(store, new ImmediateTransactionManager()).runDueBatch();

        assertThat(reconciled).containsExactly("ok");
        assertThat(failed).containsExactly("poison|IllegalStateException: bad row|8");
    }

    private static final class ImmediateTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}

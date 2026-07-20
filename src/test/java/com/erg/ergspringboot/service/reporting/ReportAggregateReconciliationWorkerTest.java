package com.erg.ergspringboot.service.reporting;

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
        };

        int processed = new ReportAggregateReconciliationWorker(store, new ImmediateTransactionManager())
                .runDueBatch();

        assertThat(processed).isEqualTo(2);
        assertThat(observedTenants).containsExactly("tenant-a", "tenant-b");
        assertThat(TenantContext.current()).isNull();
    }

    private static final class ImmediateTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}

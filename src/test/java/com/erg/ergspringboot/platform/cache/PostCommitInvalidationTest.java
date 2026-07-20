package com.erg.ergspringboot.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PostCommitInvalidationTest {

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishesOnceAfterCommitAndNothingBeforeCommit() {
        LocalCacheGenerationService generations = new LocalCacheGenerationService();
        List<CacheInvalidationMessage> published = new ArrayList<>();
        CacheInvalidationService service = new CacheInvalidationService(generations, published::add);
        TransactionSynchronizationManager.initSynchronization();

        service.clearTenantCache("tenant-a", "studentDashboard");
        service.clearTenantCache("tenant-a", "studentDashboard");

        assertThat(published).isEmpty();
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        assertThat(published).hasSize(1);
        assertThat(published.getFirst().generation()).isEqualTo(1);
    }

    @Test
    void rollbackDoesNotPublishOrIncrementGeneration() {
        LocalCacheGenerationService generations = new LocalCacheGenerationService();
        List<CacheInvalidationMessage> published = new ArrayList<>();
        CacheInvalidationService service = new CacheInvalidationService(generations, published::add);
        TransactionSynchronizationManager.initSynchronization();

        service.clearTenantCache("tenant-a", "studentDashboard");
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        assertThat(published).isEmpty();
        assertThat(generations.current("tenant-a", "studentDashboard")).isZero();
    }
}

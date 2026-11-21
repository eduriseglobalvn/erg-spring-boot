package com.erg.ergspringboot.enterpriseconnectors.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorRunRepository;
import com.erg.ergspringboot.platform.events.ClusterJobLock;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

class EnterpriseConnectorRunSweepJobTest {
    @Test
    @SuppressWarnings("unchecked")
    void runsOlderThanTheTimeoutAreMovedToRetryable() {
        EnterpriseConnectorRunRepository runs = mock(EnterpriseConnectorRunRepository.class);
        when(runs.failStuckRuns(any(), any())).thenReturn(2);
        ObjectProvider<ClusterJobLock> lock = mock(ObjectProvider.class);
        EnterpriseConnectorRunSweepJob job = new EnterpriseConnectorRunSweepJob(
                runs, mock(PlatformTransactionManager.class), lock, Duration.ofMinutes(30));

        assertThat(job.run()).isEqualTo(2);

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> now = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(runs).failStuckRuns(cutoff.capture(), now.capture());
        assertThat(Duration.between(cutoff.getValue(), now.getValue())).isEqualTo(Duration.ofMinutes(30));
    }
}

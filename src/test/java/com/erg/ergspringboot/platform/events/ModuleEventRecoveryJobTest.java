package com.erg.ergspringboot.platform.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;

class ModuleEventRecoveryJobTest {

    @Test
    void resubmitsFailedAndIncompletePublicationsWithBoundedOptions() {
        FailedEventPublications failed = org.mockito.Mockito.mock(FailedEventPublications.class);
        IncompleteEventPublications incomplete = org.mockito.Mockito.mock(IncompleteEventPublications.class);
        ModuleEventRecoveryJob job = new ModuleEventRecoveryJob(failed, incomplete, 100, 16, Duration.ofSeconds(30));

        job.recover();

        ArgumentCaptor<ResubmissionOptions> failedOptions = ArgumentCaptor.forClass(ResubmissionOptions.class);
        ArgumentCaptor<ResubmissionOptions> incompleteOptions = ArgumentCaptor.forClass(ResubmissionOptions.class);
        verify(failed).resubmit(failedOptions.capture());
        verify(incomplete).resubmitIncompletePublications(incompleteOptions.capture());

        assertThat(failedOptions.getValue().getBatchSize()).isEqualTo(100);
        assertThat(failedOptions.getValue().getMaxInFlight()).isEqualTo(16);
        assertThat(failedOptions.getValue().getMinAge()).isEqualTo(Duration.ofSeconds(30));
        assertThat(incompleteOptions.getValue().getBatchSize()).isEqualTo(100);
        assertThat(incompleteOptions.getValue().getMaxInFlight()).isEqualTo(16);
        assertThat(incompleteOptions.getValue().getMinAge()).isEqualTo(Duration.ofSeconds(30));
    }
}

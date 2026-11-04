package com.erg.ergspringboot.lms.internal.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.persistence.AttemptExpiryRepository;
import com.erg.ergspringboot.lms.persistence.AttemptExpiryRepository.ExpiredAttemptRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttemptExpirySweepJobTest {

    @Test
    void oneFailingAttemptDoesNotStopTheBatch() {
        AttemptExpiryRepository repository = mock(AttemptExpiryRepository.class);
        LmsAttemptUseCase useCase = mock(LmsAttemptUseCase.class);
        when(repository.findExpiredCandidates(any(), anyInt())).thenReturn(List.of(ref("t1", "a1"), ref("t1", "a2"), ref("t2", "a3")));
        when(useCase.closeIfExpired(eq("t1"), eq("a1"), any())).thenThrow(new IllegalStateException("concurrent submit"));
        when(useCase.closeIfExpired(eq("t1"), eq("a2"), any())).thenReturn(true);
        when(useCase.closeIfExpired(eq("t2"), eq("a3"), any())).thenReturn(false);

        int closed = new AttemptExpirySweepJob(repository, useCase, true, 50, (com.erg.ergspringboot.platform.events.ClusterJobLock) null).closeExpired();

        assertThat(closed).isEqualTo(1);
        verify(useCase).closeIfExpired(eq("t2"), eq("a3"), any());
    }

    @Test
    void disabledJobDoesNothing() {
        AttemptExpiryRepository repository = mock(AttemptExpiryRepository.class);
        LmsAttemptUseCase useCase = mock(LmsAttemptUseCase.class);

        new AttemptExpirySweepJob(repository, useCase, false, 50, (com.erg.ergspringboot.platform.events.ClusterJobLock) null).sweep();

        verifyNoInteractions(repository, useCase);
    }

    private static ExpiredAttemptRef ref(String tenant, String id) {
        return new ExpiredAttemptRef() {
            @Override public String getTenantId() { return tenant; }
            @Override public String getId() { return id; }
        };
    }
}

package com.erg.ergspringboot.elearning.internal.engagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.elearning.internal.engagement.StudentTierService.Cohort;
import com.erg.ergspringboot.platform.events.ClusterJobLock;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** The admin's manual tier update shares the weekly job's cluster lock: a double click must not run every cohort twice. */
class StudentTierJobTest {

    private final StudentTierService tiers = mock(StudentTierService.class);
    private final ClusterJobLock lock = mock(ClusterJobLock.class);

    @SuppressWarnings("unchecked")
    private StudentTierJob job() {
        ObjectProvider<ClusterJobLock> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(lock);
        return new StudentTierJob(tiers, provider);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aBusyLockReturnsZeroWithoutTouchingAnyCohort() {
        when(lock.callExclusively(eq(StudentTierJob.JOB_NAME), any(Supplier.class))).thenReturn(null);

        assertThat(job().runForTenant("t1")).isZero();

        verifyNoInteractions(tiers);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theLockHolderRunsOnlyTheCallersTenantAndReturnsTheTieredStudents() {
        Cohort mine = new Cohort("t1", "school-1", "6");
        Cohort other = new Cohort("t2", "school-2", "6");
        when(lock.callExclusively(eq(StudentTierJob.JOB_NAME), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(1)).get());
        when(tiers.cohorts()).thenReturn(List.of(mine, other));
        when(tiers.refreshCohort(eq(mine), any())).thenReturn(25);

        assertThat(job().runForTenant("t1")).isEqualTo(25);

        verify(tiers).refreshCohort(eq(mine), any());
        org.mockito.Mockito.verify(tiers, org.mockito.Mockito.never()).refreshCohort(eq(other), any());
    }
}

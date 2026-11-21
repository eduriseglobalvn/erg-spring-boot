package com.erg.ergspringboot.lms;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.platform.cache.CacheInvalidationService;
import com.erg.ergspringboot.platform.cache.ErgTieredCache;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

class LmsReadCacheInvalidatorScopeTest {
    private final CacheManager manager = mock(CacheManager.class);
    private final CacheInvalidationService invalidations = mock(CacheInvalidationService.class);
    private final LmsReadCacheInvalidator invalidator = new LmsReadCacheInvalidator(manager, invalidations);

    LmsReadCacheInvalidatorScopeTest() {
        when(manager.getCache(anyString())).thenAnswer(call -> mock(ErgTieredCache.class));
    }

    @Test
    void anAttemptInvalidatesOnlyItsTeacherAssignmentQuizAndStudent() {
        invalidator.evictAttemptAfterCommit("t1", attempt(), assignment(), "user-s1");

        verify(invalidations).clearTenantCacheScope("t1", "lmsHomeworkWorkspace", "teacher-1");
        verify(invalidations).clearTenantCacheScope("t1", "lmsAssignmentProgress", "asg-1");
        verify(invalidations).clearTenantCacheScope("t1", "quizAssignmentReport", "asg-1");
        verify(invalidations).clearTenantCacheScope("t1", "quizQuestionAnalytics", "quiz-1");
        verify(invalidations).clearTenantCacheScope("t1", "quizQuestionAnalytics", "qv-1");
        verify(invalidations).clearTenantCacheScope("t1", "studentDashboard", "user-s1");
        verify(invalidations).clearTenantCacheScope("t1", "studentTier", "user-s1");
        verify(invalidations).clearTenantCacheScope("t1", "lmsClassWorkspace", "class-1");
        verify(invalidations, never()).clearTenantCache(eq("t1"), eq("lmsClassWorkspace"));
        verify(invalidations, never()).clearTenantCache(eq("t1"), eq("studentDashboard"));
        verify(invalidations, never()).clearTenantCache(eq("t1"), eq("lmsHomeworkWorkspace"));
    }

    @Test
    void aClassLessAssignmentClearsEveryClassWorkspaceOfTheTenant() {
        Assignment classless = assignment();
        classless.setAcademicClassId(null);

        invalidator.evictAttemptAfterCommit("t1", attempt(), classless, "user-s1");

        verify(invalidations).clearTenantCache("t1", "lmsClassWorkspace");
    }

    @Test
    void aRosterWriteClearsOnlyThoseClassWorkspaces() {
        invalidator.evictClassWorkspaces("t1", "class-1", null, "class-2");

        verify(invalidations).clearTenantCacheScope("t1", "lmsClassWorkspace", "class-1");
        verify(invalidations).clearTenantCacheScope("t1", "lmsClassWorkspace", "class-2");
        verify(invalidations, never()).clearTenantCache(eq("t1"), eq("lmsClassWorkspace"));
    }

    @Test
    void anAttemptAndARosterWriteAlsoDropTheScoreBoardOfThatClass() {
        invalidator.evictAttemptAfterCommit("t1", attempt(), assignment(), "user-s1");
        invalidator.evictClassWorkspaces("t2", "class-9");

        verify(invalidations).clearTenantCacheScope("t1", "lmsClassScoreBoard", "class-1");
        verify(invalidations).clearTenantCacheScope("t2", "lmsClassScoreBoard", "class-9");
        verify(invalidations, never()).clearTenantCache(eq("t1"), eq("lmsClassScoreBoard"));
    }

    @Test
    void anUnknownScopeFallsBackToTheWholeTenant() {
        invalidator.evictAttemptAfterCommit("t1", attempt(), assignment(), null);

        verify(invalidations).clearTenantCache("t1", "studentDashboard");
        verify(invalidations, never()).clearTenantCacheScope(eq("t1"), eq("studentDashboard"), anyString());
    }

    @Test
    void aTenantWideEvictionDropsLocalEntriesNowInsideATransactionAndAgainAfterCommit() {
        ErgTieredCache cache = mock(ErgTieredCache.class);
        when(manager.getCache("studentDashboard")).thenReturn(cache);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            invalidator.evictAfterCommit("t1", java.util.List.of("studentDashboard"));
            verify(cache, org.mockito.Mockito.times(1)).clearLocal("t1");

            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(cache, org.mockito.Mockito.times(2)).clearLocal("t1");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static Attempt attempt() {
        Attempt attempt = new Attempt();
        attempt.setAssignmentId("asg-1");
        attempt.setQuizId("quiz-1");
        attempt.setQuizVersionId("qv-1");
        attempt.setStudentId("stu-1");
        return attempt;
    }

    private static Assignment assignment() {
        Assignment assignment = new Assignment();
        assignment.setId("asg-1");
        assignment.setAssignedBy("teacher-1");
        assignment.setAcademicClassId("class-1");
        return assignment;
    }
}

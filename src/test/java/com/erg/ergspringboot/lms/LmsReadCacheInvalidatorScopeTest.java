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
        verify(invalidations).clearTenantCache("t1", "lmsClassWorkspace");
        verify(invalidations, never()).clearTenantCache(eq("t1"), eq("studentDashboard"));
        verify(invalidations, never()).clearTenantCache(eq("t1"), eq("lmsHomeworkWorkspace"));
    }

    @Test
    void anUnknownScopeFallsBackToTheWholeTenant() {
        invalidator.evictAttemptAfterCommit("t1", attempt(), assignment(), null);

        verify(invalidations).clearTenantCache("t1", "studentDashboard");
        verify(invalidations, never()).clearTenantCacheScope(eq("t1"), eq("studentDashboard"), anyString());
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
        return assignment;
    }
}

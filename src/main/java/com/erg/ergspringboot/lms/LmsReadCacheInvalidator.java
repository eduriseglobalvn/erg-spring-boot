package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.platform.cache.CacheInvalidationService;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.platform.cache.ErgTieredCache;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tenant-scoped invalidation of LMS read caches. Instead of clearing whole caches for every tenant, it bumps
 * the per-tenant cache generation (shared {@link CacheInvalidationService}, applied after commit) and drops only
 * that tenant's local L1 entries once the write transaction committed.
 */
@Component
public class LmsReadCacheInvalidator {
    public static final List<String> ATTEMPT_READ_CACHES = List.of("lmsHomeworkWorkspace", "lmsClassWorkspace",
            "lmsAssignmentProgress", "studentDashboard", "quizAssignmentReport", "quizQuestionAnalytics", "studentTier", "lmsClassScoreBoard");
    public static final List<String> ASSIGNMENT_READ_CACHES = List.of("lmsHomeworkWorkspace", "lmsClassWorkspace",
            "lmsAssignmentProgress", "lmsStudentAssignments", "studentDashboard", "quizAssignmentReport",
            "quizQuestionAnalytics");

    private final CacheManager caches;
    private final CacheInvalidationService invalidations;

    @org.springframework.beans.factory.annotation.Autowired
    public LmsReadCacheInvalidator(CacheManager caches, ObjectProvider<CacheInvalidationService> invalidations) {
        this(caches, invalidations == null ? null : invalidations.getIfAvailable());
    }

    public LmsReadCacheInvalidator(CacheManager caches, CacheInvalidationService invalidations) {
        this.caches = caches;
        this.invalidations = invalidations;
    }

    public void evictAfterCommit(String tenantId, Collection<String> cacheNames) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        String tenant = tenantId.trim();
        for (String name : cacheNames) {
            Cache cache = caches.getCache(name);
            // The generation bump defers itself to afterCommit when a transaction is active.
            if (cache instanceof ErgTieredCache && invalidations != null) invalidations.clearTenantCache(tenant, name);
        }
        Runnable local = () -> cacheNames.forEach(name -> clearLocal(tenant, name));
        // Now too, so a re-read in this transaction misses; after commit again for readers in between.
        if (TransactionSynchronizationManager.isSynchronizationActive()) local.run();
        afterCommit(local);
    }

    /**
     * P2-10: an attempt change invalidates only the reads it can alter (its teacher's homework workspace, its
     * assignment's progress and report, its quiz analytics, its student's dashboard), so a submission burst no
     * longer empties every other teacher's and student's cache in the tenant. Unknown scopes fall back to tenant-wide.
     */
    public void evictAttemptAfterCommit(String tenantId, Attempt attempt, Assignment assignment, String studentUserId) {
        Map<String, List<String>> scopes = new LinkedHashMap<>();
        scopes.put("lmsHomeworkWorkspace", ids(assignment == null ? null : assignment.getAssignedBy()));
        scopes.put("lmsAssignmentProgress", ids(attempt.getAssignmentId()));
        scopes.put("quizAssignmentReport", ids(attempt.getAssignmentId()));
        scopes.put("quizQuestionAnalytics", ids(attempt.getQuizId(), attempt.getQuizVersionId()));
        scopes.put("studentDashboard", ids(studentUserId));
        scopes.put("studentTier", ids(studentUserId)); // XP/hand-in count must move right after a hand-in
        // Class workspace scores/risks are keyed by the assignment's class; a class-less assignment falls back to tenant-wide.
        scopes.put("lmsClassWorkspace", ids(assignment == null ? null : assignment.getAcademicClassId()));
        scopes.put("lmsClassScoreBoard", ids(assignment == null ? null : assignment.getAcademicClassId()));
        List<String> tenantWide = ATTEMPT_READ_CACHES.stream().filter(name -> scopes.getOrDefault(name, List.of()).isEmpty()).toList();
        if (!tenantWide.isEmpty()) evictAfterCommit(tenantId, tenantWide);
        scopes.values().removeIf(List::isEmpty);
        evictScopesAfterCommit(tenantId, scopes);
    }

    /** Roster/class writes: drops only these classes' workspaces (null ids are ignored). */
    public void evictClassWorkspaces(String tenantId, String... classIds) {
        List<String> ids = ids(classIds);
        if (!ids.isEmpty()) evictScopesAfterCommit(tenantId, Map.of("lmsClassWorkspace", ids, "lmsClassScoreBoard", ids));
    }

    /**
     * A student's class, school, status, name or login link changed: drops the cached login -> student lookup of these
     * logins (the old and the new one when the link moved; null ids are ignored).
     */
    public void evictStudentIdentity(String tenantId, String... authUserIds) {
        List<String> ids = ids(authUserIds);
        if (!ids.isEmpty()) evictScopesAfterCommit(tenantId, Map.of("lmsStudentIdentity", ids));
    }

    public void evictScopesAfterCommit(String tenantId, Map<String, List<String>> scopesByCache) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        String tenant = tenantId.trim();
        scopesByCache.forEach((name, scopes) -> {
            if (caches.getCache(name) instanceof ErgTieredCache && invalidations != null) {
                scopes.forEach(scope -> invalidations.clearTenantCacheScope(tenant, name, scope));
            }
        });
        // Now too: a read later in the same transaction must not be served the pre-write entry (the shared generation
        // bump is deferred to commit, so only this instance's L1 can be fixed here); after commit again for readers in between.
        Runnable local = () -> scopesByCache.forEach((name, scopes) -> {
            Cache cache = caches.getCache(name);
            if (cache instanceof ErgTieredCache tiered && invalidations != null) scopes.forEach(scope -> tiered.clearLocal(tenant, scope));
            else if (cache != null) cache.clear();
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) local.run();
        afterCommit(local);
    }

    private static List<String> ids(String... values) {
        return Stream.of(values).filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void clearLocal(String tenant, String name) {
        Cache cache = caches.getCache(name);
        if (cache == null) return;
        if (cache instanceof ErgTieredCache tiered) {
            tiered.clearLocal(tenant);
            if (invalidations == null) tiered.clear();
            return;
        }
        // Non-tiered caches (tests/tools) have no tenant generation; fall back to a full clear.
        cache.clear();
    }
}

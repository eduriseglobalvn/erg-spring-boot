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
            "lmsAssignmentProgress", "studentDashboard", "quizAssignmentReport", "quizQuestionAnalytics");
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
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            local.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                local.run();
            }
        });
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
        List<String> tenantWide = ATTEMPT_READ_CACHES.stream().filter(name -> scopes.getOrDefault(name, List.of()).isEmpty()).toList();
        if (!tenantWide.isEmpty()) evictAfterCommit(tenantId, tenantWide);
        scopes.values().removeIf(List::isEmpty);
        evictScopesAfterCommit(tenantId, scopes);
    }

    void evictScopesAfterCommit(String tenantId, Map<String, List<String>> scopesByCache) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        String tenant = tenantId.trim();
        scopesByCache.forEach((name, scopes) -> {
            if (caches.getCache(name) instanceof ErgTieredCache && invalidations != null) {
                scopes.forEach(scope -> invalidations.clearTenantCacheScope(tenant, name, scope));
            }
        });
        afterCommit(() -> scopesByCache.forEach((name, scopes) -> {
            Cache cache = caches.getCache(name);
            if (cache instanceof ErgTieredCache tiered && invalidations != null) scopes.forEach(scope -> tiered.clearLocal(tenant, scope));
            else if (cache != null) cache.clear();
        }));
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

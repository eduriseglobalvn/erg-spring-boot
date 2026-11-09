package com.erg.ergspringboot.lcms.quiz.publish;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/** Evicts the quiz runtime-package and LCMS workspace caches once a publish/fork commits. */
final class QuizPublishCacheEvictor {

    private final CacheManager cacheManager;

    QuizPublishCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Clears in the transaction: ErgTieredCache.clear() defers its own generation bump to after commit. Running it from
     * an afterCommit callback (as before) registered that bump too late, so Redis kept serving the old lists.
     */
    void evictQuizCachesAfterCommit(String tenantId, String quizId) {
        evictAll("quizRuntimePackage");
        evictAll("lcmsQuestionBankWorkspace");
        evictAll("lcmsQuizWorkspace");
    }

    private void evictAll(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}

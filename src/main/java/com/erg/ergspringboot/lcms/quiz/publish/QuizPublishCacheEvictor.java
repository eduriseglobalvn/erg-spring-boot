package com.erg.ergspringboot.lcms.quiz.publish;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Evicts the quiz runtime-package and LCMS workspace caches once a publish/fork commits. */
final class QuizPublishCacheEvictor {

    private final CacheManager cacheManager;

    QuizPublishCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    void evictQuizCachesAfterCommit(String tenantId, String quizId) {
        Runnable evict = () -> {
            Cache cache = cacheManager.getCache("quizRuntimePackage");
            if (cache != null) {
                cache.evict(tenantId + ":" + quizId);
            }
            evictAll("lcmsQuestionBankWorkspace");
            evictAll("lcmsQuizWorkspace");
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evict.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evict.run();
            }
        });
    }

    private void evictAll(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}

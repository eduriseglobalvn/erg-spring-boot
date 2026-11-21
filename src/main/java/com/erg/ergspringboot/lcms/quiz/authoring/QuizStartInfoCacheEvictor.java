package com.erg.ergspringboot.lcms.quiz.authoring;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

/** Separate bean so the eviction goes through the cache proxy and can be called conditionally from a transaction. */
@Component
public class QuizStartInfoCacheEvictor {

    @CacheEvict(cacheNames = "quizStartInfo", allEntries = true)
    public void evictAll() {
        // eviction is declared by the annotation
    }
}

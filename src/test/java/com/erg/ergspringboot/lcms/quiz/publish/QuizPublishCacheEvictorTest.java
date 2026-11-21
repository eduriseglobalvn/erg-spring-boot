package com.erg.ergspringboot.lcms.quiz.publish;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

class QuizPublishCacheEvictorTest {
    private final CacheManager manager = mock(CacheManager.class);
    private final Cache startInfo = mock(Cache.class);
    private final Cache runtimePackage = mock(Cache.class);
    private final Cache workspace = mock(Cache.class);

    QuizPublishCacheEvictorTest() {
        when(manager.getCache("quizStartInfo")).thenReturn(startInfo);
        when(manager.getCache("quizRuntimePackage")).thenReturn(runtimePackage);
        when(manager.getCache("lcmsQuizWorkspace")).thenReturn(workspace);
    }

    @Test
    void aForkLeavesTheCachesStudentsStartAttemptsFromAlone() {
        new QuizPublishCacheEvictor(manager).evictAuthoringCachesAfterCommit();

        verify(workspace).clear();
        verify(startInfo, never()).clear();
        verify(runtimePackage, never()).clear();
    }

    @Test
    void aPublishStillClearsTheStudentCaches() {
        new QuizPublishCacheEvictor(manager).evictQuizCachesAfterCommit("t", "q");

        verify(startInfo).clear();
        verify(runtimePackage).clear();
    }
}

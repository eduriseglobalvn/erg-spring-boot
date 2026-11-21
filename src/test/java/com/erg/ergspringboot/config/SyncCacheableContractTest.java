package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;

/** Shared-key caches load once per key under a stampede (sync = true); Spring forbids sync with unless or several caches. */
class SyncCacheableContractTest {

    private static final String ROOT = "com.erg.ergspringboot.";
    private static final Map<String, String> SYNC_CACHEABLES = Map.of(
            "learningcontent.library.LearningResourceLibraryReadService", "learningResourceLibrary",
            "curriculum.CurriculumOperations", "curriculumTree",
            "school.internal.read.SchoolOperationsReadService", "operations",
            "lms.internal.readmodel.LmsAssignmentProgressQueryUseCase", "workspace",
            "lms.internal.readmodel.LmsClassWorkspaceReadUseCase", "query",
            "elearning.internal.engagement.StudentTierReader", "myTier",
            "notifications.internal.NotificationService", "unreadCount");

    @Test
    void sharedKeyCacheablesAreSyncAndCompatibleWithIt() throws Exception {
        for (var entry : SYNC_CACHEABLES.entrySet()) {
            Class<?> type = Class.forName(ROOT + entry.getKey());
            var methods = Arrays.stream(type.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(Cacheable.class))
                    .filter(m -> m.getName().equals(entry.getValue()) || m.getAnnotation(Cacheable.class).cacheNames()[0].equals(entry.getValue()))
                    .toList();
            assertThat(methods).as(type.getSimpleName()).isNotEmpty();
            for (var method : methods) {
                Cacheable cacheable = method.getAnnotation(Cacheable.class);
                assertThat(cacheable.sync()).as(type.getSimpleName() + "." + method.getName()).isTrue();
                assertThat(cacheable.unless()).as("sync cannot combine with unless").isEmpty();
                assertThat(cacheable.cacheNames()).hasSize(1);
            }
        }
    }
}

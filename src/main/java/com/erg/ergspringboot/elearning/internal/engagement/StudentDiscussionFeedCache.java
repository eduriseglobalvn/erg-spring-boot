package com.erg.ergspringboot.elearning.internal.engagement;

import com.erg.ergspringboot.common.response.elearning.DiscussionFeedSnapshot;
import com.erg.ergspringboot.elearning.internal.engagement.StudentDiscussionReader.PostFilter;
import com.erg.ergspringboot.lms.LmsReadCacheInvalidator;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Short-lived cache of the class-shared part of a discussion feed page, so a class opening the tab together shares one read.
 * Key: tenant, class, page, size, filter kind, subject, author (only for MINE) and includeHidden; scoped by class. Every writer
 * of a post, comment, reaction, pin, answer, hide or delete calls {@link #evictClass} (after commit and now, per class).
 * It is a separate bean so the {@code @Cacheable} proxy applies to calls from the services.
 */
@Component
public class StudentDiscussionFeedCache {

    static final String CACHE = "elearningDiscussionFeed";

    private final StudentDiscussionReader reader;
    private final LmsReadCacheInvalidator caches;

    public StudentDiscussionFeedCache(JdbcTemplate jdbc, LmsReadCacheInvalidator caches) {
        this.reader = new StudentDiscussionReader(jdbc, new NamedParameterJdbcTemplate(jdbc));
        this.caches = caches;
    }

    /** Scope function of the descriptor reads element 1 (the class) of this key; keep both in step. */
    @Cacheable(cacheNames = CACHE, sync = true,
            key = "{#tenantId, #classId, #page, #size, #kind, #subject, #author, #includeHidden}")
    public DiscussionFeedSnapshot snapshot(String tenantId, String classId, int page, int size, String kind, String subject,
                                           String author, boolean includeHidden) {
        PostFilter filter = new PostFilter(kind, subject);
        return reader.classSnapshot(tenantId, author, classId, size, page * size, includeHidden, filter);
    }

    /** The filter key part: the viewer only matters for MINE, so every other filter is shared by the whole class. */
    static String authorKey(PostFilter filter, String actorUserId) {
        return "MINE".equals(filter.kind()) ? actorUserId : "-";
    }

    /** Drops the class's cached pages in this tenant (every page, filter and size); call from every discussion writer. */
    void evictClass(String tenantId, String classId) {
        if (classId == null || classId.isBlank()) return;
        caches.evictScopesAfterCommit(tenantId, Map.of(CACHE, List.of(classId)));
    }
}

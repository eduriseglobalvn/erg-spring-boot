package com.erg.ergspringboot.calendar;

import java.util.List;
import java.util.Optional;

/** Public, persistence-free projection consumed by downstream modules after a schedule is published. */
public interface CalendarPublishedBatchProjection {

    Optional<PublishedBatch> findPublishedBatch(String tenantId, String batchId);

    record PublishedBatch(String batchId, String schoolId, int sessionCount, List<String> teacherIds) {
        public PublishedBatch {
            teacherIds = teacherIds == null ? List.of() : List.copyOf(teacherIds);
        }
    }
}

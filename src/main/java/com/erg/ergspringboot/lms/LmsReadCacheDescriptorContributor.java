package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.lms.LmsAcademicRecordResponses.AttendanceWorkspaceResponse;
import com.erg.ergspringboot.lms.LmsClassScoreBoardResponses.ClassScoreBoardResponse;
import com.erg.ergspringboot.platform.cache.CacheDescriptor;
import com.erg.ergspringboot.platform.cache.CacheDescriptorContributor;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/** Class-scoped teacher reads: both are keyed {tenantId, classId, ...} so a class write drops exactly that class (see LmsReadCacheInvalidator). */
@Component
public class LmsReadCacheDescriptorContributor implements CacheDescriptorContributor {
    /** Finished results of a class; evicted with the attempt caches. */
    static final String CLASS_SCORE_BOARD = "lmsClassScoreBoard";
    static final String ATTENDANCE_WORKSPACE = "lmsAttendanceWorkspace";

    @Override
    public List<CacheDescriptor<?>> descriptors(ObjectMapper mapper) {
        return List.of(
                descriptor(CLASS_SCORE_BOARD, mapper.constructType(ClassScoreBoardResponse.class), 32),
                descriptor(ATTENDANCE_WORKSPACE, mapper.constructType(AttendanceWorkspaceResponse.class), 32));
    }

    private static CacheDescriptor<Object> descriptor(String name, JavaType type, long l1MiB) {
        Duration ttl = Duration.ofSeconds(30);
        return new CacheDescriptor<>(name, type, ttl, ttl.multipliedBy(4), l1MiB * 1024 * 1024, 1024 * 1024, true, false, classScope());
    }

    /** The class id: element 1 of the {@code {#tenantId, #classId, ...}} cache key. */
    static Function<Object, String> classScope() {
        return key -> key instanceof List<?> parts && parts.size() > 1 && parts.get(1) != null ? parts.get(1).toString() : null;
    }
}

package com.erg.ergspringboot.config;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarWorkspaceResponse;
import com.erg.ergspringboot.common.response.learningcontent.LearningResourceLibraryResponses.LearningResourceLibraryResponse;
import com.erg.ergspringboot.common.response.organization.EducationUnitResponses.EducationUnitListResponse;
import com.erg.ergspringboot.common.response.school.SchoolOperationsResponses.SchoolDirectoryWorkspace;
import com.erg.ergspringboot.common.response.school.SchoolOperationsResponses.SchoolOperationsWorkspace;
import com.erg.ergspringboot.curriculum.CurriculumDtos.CurriculumTreeResponse;
import com.erg.ergspringboot.platform.cache.CacheDescriptor;
import com.erg.ergspringboot.platform.cache.CacheDescriptorContributor;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/** Cache metadata for organization, school, calendar, curriculum and learning-content readers. */
@Component
public class PlatformCacheDescriptorContributor implements CacheDescriptorContributor {
    @Override
    public List<CacheDescriptor<?>> descriptors(ObjectMapper mapper) {
        JavaType untyped = mapper.constructType(Object.class);
        return List.of(
                descriptor("contentLibraryBootstrap", untyped, Duration.ofSeconds(60), 32),
                descriptor("contentStudioBootstrap", untyped, Duration.ofSeconds(60), 16),
                descriptor("contentResources", untyped, Duration.ofSeconds(60), 32),
                descriptor("contentResourceDetail", untyped, Duration.ofSeconds(60), 32),
                // Scoped by school so one school's class/student write keeps the other schools' entries.
                descriptor("lmsClassesBySchool", untyped, Duration.ofSeconds(60), 32, LmsCacheDescriptorContributor.keyPart(1)),
                descriptor("organizationEducationUnits", mapper.constructType(EducationUnitListResponse.class), Duration.ofSeconds(60), 32),
                descriptor("organizationEducationUnitEntities", untyped, Duration.ofSeconds(60), 32),
                descriptor("authorizationRoles", untyped, Duration.ofSeconds(60), 16),
                descriptor("authorizationPermissions", untyped, Duration.ofSeconds(60), 16),
                descriptor("lcmsSchoolDirectory", mapper.constructType(SchoolDirectoryWorkspace.class), Duration.ofSeconds(30), 64),
                descriptor("calendarWorkspace", mapper.constructType(CalendarWorkspaceResponse.class), Duration.ofSeconds(30), 64),
                descriptor("curriculumTree", mapper.constructType(CurriculumTreeResponse.class), Duration.ofMinutes(15), 32),
                descriptor("learningResourceLibrary", mapper.constructType(LearningResourceLibraryResponse.class), Duration.ofSeconds(60), 128),
                descriptor("lcmsSchoolOperations", mapper.constructType(SchoolOperationsWorkspace.class), Duration.ofSeconds(30), 128,
                        LmsCacheDescriptorContributor.keyPart(1))
        );
    }

    private CacheDescriptor<Object> descriptor(String name, JavaType type, Duration ttl, long l1MiB) {
        return descriptor(name, type, ttl, l1MiB, null);
    }

    private CacheDescriptor<Object> descriptor(String name, JavaType type, Duration ttl, long l1MiB, Function<Object, String> scope) {
        return new CacheDescriptor<>(name, type, ttl, ttl.multipliedBy(4), l1MiB * 1024 * 1024,
                1024 * 1024, true, false, scope);
    }
}

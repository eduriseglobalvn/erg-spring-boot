package com.erg.ergspringboot.config;

import com.erg.ergspringboot.platform.cache.CacheDescriptor;
import com.erg.ergspringboot.platform.cache.CacheDescriptorRegistry;
import com.erg.ergspringboot.platform.cache.CacheFailurePolicy;
import com.erg.ergspringboot.platform.cache.CacheKeyFactory;
import com.erg.ergspringboot.platform.cache.CacheLoadCoordinator;
import com.erg.ergspringboot.platform.cache.CacheTenantResolver;
import com.erg.ergspringboot.platform.cache.ErgCacheProperties;
import com.erg.ergspringboot.platform.cache.ErgTieredCacheManager;
import com.erg.ergspringboot.platform.cache.JacksonCacheValueCodec;
import com.erg.ergspringboot.platform.cache.LocalCacheGenerationService;
import com.erg.ergspringboot.platform.cache.CacheGenerationService;
import com.erg.ergspringboot.platform.cache.CacheInvalidationService;
import com.erg.ergspringboot.platform.cache.BusinessCacheRuntimeHints;
import com.erg.ergspringboot.platform.cache.CacheMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardResponse;
import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.ClassWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentProgressWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.StudentGroupWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.TeacherHomeworkWorkspaceResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankWorkspaceResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizWorkspaceResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizPackageResponse;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.AssignmentReportResponse;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.QuestionAnalyticsResponse;
import com.erg.ergspringboot.common.response.school.SchoolOperationsResponses.SchoolDirectoryWorkspace;
import com.erg.ergspringboot.common.response.organization.EducationUnitResponses.EducationUnitListResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarWorkspaceResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumTreeResponse;
import com.erg.ergspringboot.common.response.learningcontent.LearningResourceLibraryResponses.LearningResourceLibraryResponse;
import com.erg.ergspringboot.common.response.school.SchoolOperationsResponses.SchoolOperationsWorkspace;
import com.erg.ergspringboot.platform.cache.NoOpRedisCacheStore;
import com.erg.ergspringboot.platform.cache.RedisCacheStore;
import com.erg.ergspringboot.platform.security.TenantContext;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableCaching
@EnableConfigurationProperties(ErgCacheProperties.class)
@ImportRuntimeHints({CaffeineNativeRuntimeHints.class, BusinessCacheRuntimeHints.class})
public class CacheConfig {

    @Bean
    ErgTieredCacheManager cacheManager(
            ObjectMapper objectMapper,
            ErgCacheProperties properties,
            ObjectProvider<RedisCacheStore> redisStore,
            CacheGenerationService generations,
            CacheInvalidationService invalidations,
            CacheMetrics metrics
    ) {
        return build(objectMapper, properties,
                redisStore.getIfAvailable(() -> NoOpRedisCacheStore.INSTANCE), generations, invalidations, metrics);
    }

    @Bean
    CacheMetrics cacheMetrics(MeterRegistry registry) {
        return new CacheMetrics(registry);
    }

    public CacheManager cacheManager() {
        return build(JsonMapper.builder().findAndAddModules().build(),
                new ErgCacheProperties(), NoOpRedisCacheStore.INSTANCE,
                new LocalCacheGenerationService(), null, null);
    }

    private ErgTieredCacheManager build(ObjectMapper mapper, ErgCacheProperties properties,
            RedisCacheStore store, CacheGenerationService generations, CacheInvalidationService invalidations,
            CacheMetrics metrics) {
        CacheDescriptorRegistry descriptors = new CacheDescriptorRegistry(descriptors(mapper));
        CacheTenantResolver tenants = () -> {
            TenantContext context = TenantContext.current();
            return context == null ? null : context.tenantId();
        };
        return new ErgTieredCacheManager(
                descriptors,
                new CacheKeyFactory(mapper),
                new JacksonCacheValueCodec(mapper),
                store,
                tenants,
                generations,
                new CacheFailurePolicy(properties.getCircuitFailureThreshold(), properties.getCircuitOpenDuration()),
                new CacheLoadCoordinator(properties.getMaxInFlightLoads()),
                properties.isL2Enabled() ? properties.getL2Names() : java.util.Set.of(),
                invalidations,
                metrics
        );
    }

    private List<CacheDescriptor<?>> descriptors(ObjectMapper mapper) {
        JavaType untyped = mapper.constructType(Object.class);
        JavaType quizPackage = mapper.getTypeFactory()
                .constructParametricType(Optional.class, QuizPackageResponse.class);
        return List.of(
                descriptor("contentLibraryBootstrap", untyped, Duration.ofSeconds(60), 32),
                descriptor("contentStudioBootstrap", untyped, Duration.ofSeconds(60), 16),
                descriptor("contentResources", untyped, Duration.ofSeconds(60), 32),
                descriptor("contentResourceDetail", untyped, Duration.ofSeconds(60), 32),
                descriptor("lmsClassesBySchool", untyped, Duration.ofSeconds(60), 32),
                descriptor("organizationEducationUnits", mapper.constructType(EducationUnitListResponse.class), Duration.ofSeconds(60), 32),
                descriptor("organizationEducationUnitEntities", untyped, Duration.ofSeconds(60), 32),
                descriptor("authorizationRoles", untyped, Duration.ofSeconds(60), 16),
                descriptor("authorizationPermissions", untyped, Duration.ofSeconds(60), 16),
                descriptor("lmsClassWorkspace", mapper.constructType(ClassWorkspaceResponse.class), Duration.ofSeconds(30), 64),
                descriptor("lmsHomeworkWorkspace", mapper.constructType(TeacherHomeworkWorkspaceResponse.class), Duration.ofSeconds(30), 64),
                descriptor("lmsAssignmentProgress", mapper.constructType(AssignmentProgressWorkspaceResponse.class), Duration.ofSeconds(30), 64),
                descriptor("lmsStudentGroups", mapper.constructType(StudentGroupWorkspaceResponse.class), Duration.ofSeconds(30), 32),
                descriptor("quizRuntimePackage", quizPackage, Duration.ofMinutes(10), 256),
                descriptor("quizQuestionAnalytics", mapper.constructType(QuestionAnalyticsResponse.class), Duration.ofSeconds(30), 64),
                descriptor("quizAssignmentReport", mapper.constructType(AssignmentReportResponse.class), Duration.ofSeconds(30), 64),
                descriptor("lcmsQuestionBankWorkspace", mapper.constructType(QuestionBankWorkspaceResponse.class), Duration.ofSeconds(30), 128),
                descriptor("lcmsQuizWorkspace", mapper.constructType(QuizWorkspaceResponse.class), Duration.ofSeconds(30), 128),
                descriptor("lcmsSchoolDirectory", mapper.constructType(SchoolDirectoryWorkspace.class), Duration.ofSeconds(30), 64),
                descriptor("lmsSchoolClasses", untyped, Duration.ofSeconds(60), 64),
                descriptor("studentDashboard", mapper.constructType(StudentDashboardResponse.class), Duration.ofSeconds(30), 64),
                descriptor("calendarWorkspace", mapper.constructType(CalendarWorkspaceResponse.class), Duration.ofSeconds(30), 64),
                descriptor("curriculumTree", mapper.constructType(CurriculumTreeResponse.class), Duration.ofMinutes(15), 32),
                descriptor("learningResourceLibrary", mapper.constructType(LearningResourceLibraryResponse.class), Duration.ofSeconds(60), 128),
                descriptor("lcmsSchoolOperations", mapper.constructType(SchoolOperationsWorkspace.class), Duration.ofSeconds(30), 128)
        );
    }

    private CacheDescriptor<Object> descriptor(
            String name,
            JavaType type,
            Duration ttl,
            long l1MiB
    ) {
        return new CacheDescriptor<>(name, type, ttl, ttl.multipliedBy(4),
                l1MiB * 1024 * 1024, 1024 * 1024, true, false);
    }
}

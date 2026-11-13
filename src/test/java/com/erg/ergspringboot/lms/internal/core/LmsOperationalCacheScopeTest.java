package com.erg.ergspringboot.lms.internal.core;

import static com.erg.ergspringboot.common.request.lms.LmsOperationalRequests.CreateClassRequest;
import static com.erg.ergspringboot.common.request.lms.LmsOperationalRequests.EnrollStudentRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.school.SchoolOperationsResponses.SchoolDirectoryWorkspace;
import com.erg.ergspringboot.common.response.school.SchoolOperationsResponses.SchoolOperationsWorkspace;
import com.erg.ergspringboot.config.LmsCacheDescriptorContributor;
import com.erg.ergspringboot.config.PlatformCacheDescriptorContributor;
import com.erg.ergspringboot.lms.LmsReadCacheInvalidator;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentEnrollment;
import com.erg.ergspringboot.lms.persistence.StudentEnrollmentRepository;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.lms.persistence.SubjectRepository;
import com.erg.ergspringboot.platform.cache.CacheDescriptor;
import com.erg.ergspringboot.platform.cache.CacheDescriptorRegistry;
import com.erg.ergspringboot.platform.cache.CacheFailurePolicy;
import com.erg.ergspringboot.platform.cache.CacheInvalidationService;
import com.erg.ergspringboot.platform.cache.CacheKeyFactory;
import com.erg.ergspringboot.platform.cache.CacheLoadCoordinator;
import com.erg.ergspringboot.platform.cache.ErgTieredCache;
import com.erg.ergspringboot.platform.cache.ErgTieredCacheManager;
import com.erg.ergspringboot.platform.cache.JacksonCacheValueCodec;
import com.erg.ergspringboot.platform.cache.LocalCacheGenerationService;
import com.erg.ergspringboot.platform.cache.NoOpRedisCacheStore;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.users.persistence.UserActivityRepository;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import tools.jackson.databind.json.JsonMapper;

/** PERF-05: a class/student write in one school must not empty the cached reads of the tenant's other schools. */
class LmsOperationalCacheScopeTest {
    private static final String TENANT = "tenant-1";
    private static final SchoolOperationsWorkspace OPERATIONS =
            new SchoolOperationsWorkspace(null, null, List.of(), List.of(), List.of(), List.of(), List.of(), null);

    private String currentTenant = TENANT;
    private final LocalCacheGenerationService generations = new LocalCacheGenerationService();
    private final ErgTieredCacheManager manager = manager();
    private final ClassroomRepository classrooms = mock(ClassroomRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final StudentEnrollmentRepository enrollments = mock(StudentEnrollmentRepository.class);
    private final EducationUnitRepository schools = mock(EducationUnitRepository.class);
    private final LmsOperationalCommandService service = new LmsOperationalCommandService(
            classrooms, students, mock(SubjectRepository.class), enrollments, schools,
            mock(UserActivityRepository.class), mock(UserRepository.class), null,
            new LmsReadCacheInvalidator(manager, new CacheInvalidationService(generations, message -> { })));
    private final UserAccessContext access =
            new UserAccessContext("actor-1", TENANT, List.of("*"), List.of("*"), null, null);

    @Test
    void creatingAClassDropsOnlyItsSchoolsCachesAndTheDirectory() {
        when(schools.findByIdAndTenantId("school-a", TENANT)).thenReturn(Optional.of(school("school-a")));
        when(classrooms.save(any(Classroom.class))).thenAnswer(call -> call.getArgument(0));
        currentTenant = "tenant-2";
        cache("lcmsSchoolOperations").put(operationsKey("school-a"), OPERATIONS);
        currentTenant = TENANT;
        fillBothSchools();

        service.createClass("school-a", new CreateClassRequest("10A", "10", "2026", null, "ACTIVE"), access);

        assertSchoolAGoneAndSchoolBKept();
        currentTenant = "tenant-2";
        assertThat(cache("lcmsSchoolOperations").get(operationsKey("school-a"))).as("another tenant's same school id").isNotNull();
    }

    @Test
    void enrollingDropsTheStudentsSchoolOnly() {
        Student student = new Student();
        student.setId("student-1");
        student.setTenantId(TENANT);
        student.setSchoolId("school-a");
        Classroom classroom = new Classroom();
        classroom.setId("class-1");
        classroom.setTenantId(TENANT);
        classroom.setSchoolId("school-a");
        when(students.findByTenantIdAndId(TENANT, "student-1")).thenReturn(Optional.of(student));
        when(classrooms.findByTenantIdAndId(TENANT, "class-1")).thenReturn(Optional.of(classroom));
        when(enrollments.save(any(StudentEnrollment.class))).thenAnswer(call -> call.getArgument(0));
        fillBothSchools();

        service.enroll(new EnrollStudentRequest("student-1", "class-1", "2026"), null, access);

        assertSchoolAGoneAndSchoolBKept();
    }

    @Test
    void aClassListRequestedWithoutSchoolIsKeyedByTheCallersCurrentSchool() throws Exception {
        String key = com.erg.ergspringboot.lms.internal.classroom.LmsClassroomQueryUseCase.class
                .getMethod("listClassesBySchool", String.class, UserAccessContext.class)
                .getAnnotation(org.springframework.cache.annotation.Cacheable.class).key();
        UserAccessContext caller = new UserAccessContext("actor-1", TENANT, List.of("school-a"), List.of(), "school-a", null);
        var context = new org.springframework.expression.spel.support.StandardEvaluationContext();
        context.setVariable("access", caller);
        var spel = new org.springframework.expression.spel.standard.SpelExpressionParser().parseExpression(key);

        for (String requested : java.util.Arrays.asList(null, " ", "school-a", " school-a ")) {
            context.setVariable("schoolId", requested);
            // Same scope as a write to school-a, so evictSchool reaches the entry (no stale class list).
            assertThat(((List<?>) spel.getValue(context)).get(1)).as("schoolId=%s", requested).isEqualTo("school-a");
        }
    }

    private void fillBothSchools() {
        for (String school : List.of("school-a", "school-b")) {
            cache("lcmsSchoolOperations").put(operationsKey(school), OPERATIONS);
            cache("lmsClassesBySchool").put(classesKey(school), "classes-" + school);
        }
        cache("lcmsSchoolDirectory").put(List.of(TENANT, "page-0"), new SchoolDirectoryWorkspace(List.of(), 0, 20, 0, 0, false, false));
    }

    private void assertSchoolAGoneAndSchoolBKept() {
        assertThat(cache("lcmsSchoolOperations").get(operationsKey("school-a"))).as("own school is not served stale").isNull();
        assertThat(cache("lmsClassesBySchool").get(classesKey("school-a"))).as("own school is not served stale").isNull();
        assertThat(cache("lcmsSchoolDirectory").get(List.of(TENANT, "page-0"))).as("directory spans schools").isNull();
        assertThat(cache("lcmsSchoolOperations").get(operationsKey("school-b")).get()).isEqualTo(OPERATIONS);
        assertThat(cache("lmsClassesBySchool").get(classesKey("school-b")).get()).isEqualTo("classes-school-b");
        // L2: only school-a's keys move to a new generation; the tenant-wide generation (all schools) stays put.
        assertThat(generations.current(TENANT, "lcmsSchoolOperations")).isZero();
        assertThat(generations.current(TENANT, ErgTieredCache.scopeGenerationName("lcmsSchoolOperations", "school-a"))).isPositive();
        assertThat(generations.current(TENANT, ErgTieredCache.scopeGenerationName("lcmsSchoolOperations", "school-b"))).isZero();
    }

    private static List<Object> operationsKey(String school) {
        return List.of(TENANT, school, true, true, true, true, Set.of("classes"));
    }

    private static List<Object> classesKey(String school) {
        return List.of(TENANT, school, "fingerprint");
    }

    private Cache cache(String name) {
        return manager.getCache(name);
    }

    private ErgTieredCacheManager manager() {
        JsonMapper mapper = JsonMapper.builder().build();
        List<CacheDescriptor<?>> descriptors = new ArrayList<>(new PlatformCacheDescriptorContributor().descriptors(mapper));
        descriptors.addAll(new LmsCacheDescriptorContributor().descriptors(mapper));
        return new ErgTieredCacheManager(new CacheDescriptorRegistry(descriptors), new CacheKeyFactory(mapper),
                new JacksonCacheValueCodec(mapper), NoOpRedisCacheStore.INSTANCE, () -> currentTenant, generations,
                new CacheFailurePolicy(3, Duration.ofSeconds(1)), new CacheLoadCoordinator(), Set.of(),
                new CacheInvalidationService(generations, message -> { }));
    }

    private static EducationUnit school(String id) {
        EducationUnit school = new EducationUnit();
        school.setId(id);
        school.setTenantId(TENANT);
        return school;
    }
}

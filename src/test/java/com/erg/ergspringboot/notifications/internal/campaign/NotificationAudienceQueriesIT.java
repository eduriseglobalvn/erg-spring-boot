package com.erg.ergspringboot.notifications.internal.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.lms.LmsAudienceReadPort;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.users.UserDirectoryPort;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/** Every query behind the audience ports runs against the real schema and honours the roll status rule. */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
@EnabledIf("dockerAvailable")
class NotificationAudienceQueriesIT {

    private static final String TENANT = "tenant-aud";
    private static final String CLASS_ID = "class-aud";

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired
    private LmsAudienceReadPort lms;

    @Autowired
    private UserDirectoryPort users;

    @Autowired
    private EntityManager entityManager;

    @Test
    void portsAnswerEmptyForUnknownTenant() {
        assertThat(lms.classIdsOf("nobody", List.of("s"), List.of("7"))).isEmpty();
        assertThat(lms.classIdsTaughtBy("nobody", "u")).isEmpty();
        assertThat(lms.studentUserIdsInClasses("nobody", List.of("c"), 10)).isEmpty();
        assertThat(lms.studentUserIdsInGroups("nobody", List.of("g"), 10)).isEmpty();
        assertThat(lms.allStudentUserIds("nobody", 10)).isEmpty();
        assertThat(lms.studentUserIdsInClassesAmong("nobody", List.of("c"), List.of("u"))).isEmpty();
        assertThat(users.findActiveStaffUserIds("nobody", PageRequest.ofSize(10))).isEmpty();
    }

    @Test
    void onlyStudentsStillOnTheRollWithALoginAreReturned() {
        seedClass();
        seedStudent("u-active", "active");
        seedStudent("u-enrolled", "ENROLLED");
        seedStudent("u-nostatus", null);
        seedStudent("u-archived", "archived");
        seedStudent("u-withdrawn", "withdrawn");
        seedStudent(null, "active");
        entityManager.flush();
        entityManager.clear();

        assertThat(lms.studentUserIdsInClasses(TENANT, List.of(CLASS_ID), 100))
                .containsExactlyInAnyOrder("u-active", "u-enrolled", "u-nostatus");
        assertThat(lms.allStudentUserIds(TENANT, 100))
                .containsExactlyInAnyOrder("u-active", "u-enrolled", "u-nostatus");
        assertThat(lms.studentUserIdsInClassesAmong(
                TENANT, List.of(CLASS_ID), List.of("u-active", "u-archived", "u-withdrawn", "nobody")))
                .containsExactly("u-active");
    }

    private void seedClass() {
        EducationUnit school = new EducationUnit();
        school.setId("school-aud");
        school.setTenantId(TENANT);
        school.setName("Audience School");
        school.setType("school");
        entityManager.persist(school);

        Classroom classroom = new Classroom();
        classroom.setId(CLASS_ID);
        classroom.setTenantId(TENANT);
        classroom.setSchoolId("school-aud");
        classroom.setName("7A");
        classroom.setGrade("7");
        classroom.setAcademicYear("2026");
        classroom.setStatus("active");
        entityManager.persist(classroom);
    }

    private void seedStudent(String authUserId, String status) {
        String key = authUserId == null ? "nologin" : authUserId;
        if (authUserId != null) {
            User user = new User();
            user.setId(authUserId);
            user.setTenantId(TENANT);
            user.setEmail(authUserId + "@example.test");
            user.setFullName(authUserId);
            user.setStatus("active");
            user.setAccountType("student");
            user.setMustChangePassword(false);
            user.setIsProfileCompleted(true);
            user.setRecoveryEmailVerified(false);
            entityManager.persist(user);
        }
        Student student = new Student();
        student.setId("stu-" + key);
        student.setTenantId(TENANT);
        student.setSchoolId("school-aud");
        student.setAcademicClassId(CLASS_ID);
        student.setStudentCode("C-" + key);
        student.setFullName(key);
        student.setUsername(key);
        student.setAuthUserId(authUserId);
        student.setStatus(status);
        entityManager.persist(student);
    }

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }
}

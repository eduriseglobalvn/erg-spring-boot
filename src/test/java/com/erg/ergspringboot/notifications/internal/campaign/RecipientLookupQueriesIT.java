package com.erg.ergspringboot.notifications.internal.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.lms.LmsStudentLookupPort;
import com.erg.ergspringboot.lms.LmsStudentLookupPort.StudentCard;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/** The student lookup port runs against the real schema: key matching, roll status, class scope, class names. */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
@EnabledIf("dockerAvailable")
class RecipientLookupQueriesIT {

    private static final String TENANT = "tenant-look";

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired
    private LmsStudentLookupPort port;

    @Autowired
    private EntityManager entityManager;

    @Test
    void keysMatchCodeEmailAndStrippedPhoneAndHonourRollAndScope() {
        EducationUnit school = new EducationUnit();
        school.setId("school-look");
        school.setTenantId(TENANT);
        school.setName("Lookup School");
        school.setType("school");
        entityManager.persist(school);
        seedClass("class-7a", "7A");
        seedClass("class-8b", "8B");
        seedStudent("a", "class-7a", "hs0712", "Minh@Erg.edu.vn", "0912 345-678", "active");
        seedStudent("b", "class-8b", "HS0800", null, "+84 988 777.666", "ENROLLED");
        seedStudent("c", "class-7a", "HS0900", null, null, "archived");
        seedStudent("d", "class-7a", "HS1000", null, null, null);
        entityManager.flush();
        entityManager.clear();

        List<StudentCard> byKeys = port.findByKeys(TENANT, null,
                List.of("HS0712", "HS0900"), List.of("minh@erg.edu.vn"), List.of("+84988777666"));
        assertThat(byKeys).extracting(StudentCard::userId).containsExactlyInAnyOrder("u-a", "u-b");
        assertThat(byKeys).filteredOn(s -> s.userId().equals("u-a")).singleElement()
                .satisfies(s -> assertThat(s.className()).isEqualTo("7A"));

        assertThat(port.findByKeys(TENANT, List.of("class-8b"), List.of("HS0712"), List.of(), List.of("0912345678")))
                .isEmpty();
        assertThat(port.findByKeys(TENANT, List.of("class-7a"), List.of(), List.of(), List.of("0912345678")))
                .extracting(StudentCard::userId).containsExactly("u-a");
        assertThat(port.findByKeys("nobody", null, List.of("HS0712"), List.of(), List.of())).isEmpty();
        assertThat(port.findByKeys(TENANT, List.of(), List.of("HS0712"), List.of(), List.of())).isEmpty();

        assertThat(port.listStudents(TENANT, null, 10)).extracting(StudentCard::userId)
                .containsExactlyInAnyOrder("u-a", "u-b", "u-d");
        assertThat(port.listStudents(TENANT, List.of("class-8b"), 10)).extracting(StudentCard::className)
                .containsExactly("8B");
        assertThat(port.listStudents(TENANT, List.of(), 10)).isEmpty();
    }

    private void seedClass(String id, String name) {
        Classroom classroom = new Classroom();
        classroom.setId(id);
        classroom.setTenantId(TENANT);
        classroom.setSchoolId("school-look");
        classroom.setName(name);
        classroom.setGrade("7");
        classroom.setAcademicYear("2026");
        classroom.setStatus("active");
        entityManager.persist(classroom);
    }

    private void seedStudent(String key, String classId, String code, String email, String phone, String status) {
        User user = new User();
        user.setId("u-" + key);
        user.setTenantId(TENANT);
        user.setEmail("u-" + key + "@example.test");
        user.setFullName("Student " + key);
        user.setStatus("active");
        user.setAccountType("student");
        user.setMustChangePassword(false);
        user.setIsProfileCompleted(true);
        user.setRecoveryEmailVerified(false);
        entityManager.persist(user);
        Student student = new Student();
        student.setId("stu-" + key);
        student.setTenantId(TENANT);
        student.setSchoolId("school-look");
        student.setAcademicClassId(classId);
        student.setStudentCode(code);
        student.setFullName("Student " + key);
        student.setUsername(key);
        student.setAuthUserId("u-" + key);
        student.setEmail(email);
        student.setPhone(phone);
        student.setStatus(status);
        entityManager.persist(student);
    }

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }
}

package com.erg.ergspringboot.lms.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/** The school student page and counts run in SQL so a school of any size is never loaded whole; check them on Postgres. */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
@EnabledIf("postgresFixtureAvailable")
class SchoolStudentQueriesPostgresTest {

    static boolean postgresFixtureAvailable() {
        String configured = System.getProperty("erg.test.postgres.url");
        if (configured == null || configured.isBlank()) configured = System.getenv("ERG_TEST_POSTGRES_URL");
        if (configured != null && !configured.isBlank()) return true;
        try { return DockerClientFactory.instance().isDockerAvailable(); }
        catch (RuntimeException unavailable) { return false; }
    }

    @Autowired private StudentRepository students;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("insert into education_units (id, created_at, updated_at, tenant_id, name)"
                + " values ('qs', now(), now(), 'qt', 'Q School'), ('other', now(), now(), 'qt', 'Other School')");
        jdbc.update("insert into academic_classes (id, created_at, updated_at, tenant_id, school_id, name, grade)"
                + " values ('qc-6a', now(), now(), 'qt', 'qs', '6A', '6'), ('qc-7a', now(), now(), 'qt', 'qs', '7A', '7')");
        jdbc.update("insert into lms_students (id, created_at, updated_at, tenant_id, school_id, academic_class_id,"
                + " student_code, full_name, parent_phone, status, metrics) values"
                + " ('qs-1', now(), now(), 'qt', 'qs', 'qc-6a', 'HS1', 'An Nguyen', '0901', 'ACTIVE', '{\"averageScore\": 8}'),"
                + " ('qs-2', now(), now(), 'qt', 'qs', 'qc-6a', null, 'Binh Tran', null, 'AT_RISK', '{\"averageScore\": 6}'),"
                + " ('qs-3', now(), now(), 'qt', 'qs', 'qc-7a', 'HS3', 'Chi Le', null, 'GRADUATED', null),"
                + " ('qs-4', now(), now(), 'qt', 'qs', null, 'HS4', 'Dung Pham', null, null, '{}'),"
                + " ('qs-x', now(), now(), 'qt', 'other', 'qc-6a', 'HSX', 'An Other', null, 'ACTIVE', null)");
    }

    @Test
    void pagesAndFiltersInSql() {
        var all = students.findSchoolStudents("qt", "qs", null, null, null, null, PageRequest.of(0, 2));
        assertThat(all.getTotalElements()).isEqualTo(4);
        assertThat(all.getContent()).extracting(Student::getId).containsExactly("qs-1", "qs-2");

        assertThat(students.findSchoolStudents("qt", "qs", "%an%", null, null, null, PageRequest.of(0, 10)).getContent())
                .extracting(Student::getId).containsExactly("qs-1", "qs-2"); // "An Nguyen", "Binh Tran"
        assertThat(students.findSchoolStudents("qt", "qs", null, "6", "6A", "studying", PageRequest.of(0, 10)).getContent())
                .extracting(Student::getId).containsExactly("qs-1");
        assertThat(students.findSchoolStudents("qt", "qs", null, null, null, "inactive", PageRequest.of(0, 10)).getContent())
                .extracting(Student::getId).containsExactly("qs-3", "qs-4");
    }

    @Test
    void countsWithoutLoadingStudents() {
        var stats = students.summarizeSchoolStudents("qt", "qs", null, null, null, null, List.of("qc-6a"));
        assertThat(List.of(stats.total(), stats.atRisk(), stats.studying(), stats.profileComplete(), stats.assigned()))
                .containsExactly(4L, 1L, 1L, 2L, 2L);
        var none = students.summarizeSchoolStudents("qt", "qs", null, "9", null, "at-risk", List.of("__none__"));
        assertThat(List.of(none.total(), none.atRisk(), none.assigned())).containsExactly(0L, 0L, 0L);

        assertThat(students.countSchoolStudentsByClass("qt", "qs"))
                .extracting(row -> row[0] + "=" + ((Number) row[1]).longValue())
                .containsExactlyInAnyOrder("qc-6a=2", "qc-7a=1");
        Object[] score = students.scoreSchoolStudents("qt", "qs").getFirst();
        assertThat(((Number) score[0]).longValue()).isEqualTo(2);
        assertThat(((Number) score[1]).doubleValue()).isEqualTo(7.0);
    }
}

package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ScaleFixtureContractTest {

    @Test
    void deterministicFixtureCoversEveryApprovedFiveThousandUserCardinality() throws Exception {
        String manifest = Files.readString(Path.of("load/fixtures/manifest.yaml"));
        String sql = Files.readString(Path.of("load/fixtures/seed-postgres-scale.sql")).toLowerCase();

        assertThat(manifest).contains(
                "students: 4500", "teachers: 450", "admins: 50", "classes: 250",
                "subjects: 20", "assignments_per_class: 40", "quizzes: 10", "questions_per_quiz: 50",
                "learning_resources: 1000",
                "school_operational_profiles: 10", "school_academic_years: 20", "school_grades: 120",
                "curriculum_topics: 1000", "content_placements: 1000",
                "quiz_versions: 10", "quiz_version_questions: 500",
                "attempt_answer_rows: 900000", "report_jobs: 50",
                "student_progress_rows: 180000",
                "load_sessions: 5000", "submission_burst_attempts: 5000",
                "calendar_rows: 100000", "history_rows: 1000000");
        assertThat(sql).contains(
                "insert into curriculum_subjects",
                "insert into school_operational_profiles",
                "insert into school_academic_years",
                "insert into school_grades",
                "jsonb_build_object('schoolid'",
                "insert into lms_questions",
                "insert into lms_quizzes",
                "insert into lms_quiz_questions",
                "insert into quiz_versions",
                "insert into quiz_version_sections",
                "insert into quiz_version_questions",
                "insert into attempt_answers",
                "insert into topic_content_items",
                "insert into report_jobs",
                "insert into student_progress",
                "insert into user_sessions",
                "insert into content_items",
                "insert into teaching_schedule_events",
                "generate_series(1, 100000)",
                "generate_series(1, 1000000)");
    }
}

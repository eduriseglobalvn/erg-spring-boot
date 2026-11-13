package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 2000-CCU exam-day gate (S2 login storm, S3 exam session, S5 teacher progress). Pins the SLO thresholds
 * from docs/performance/2026-07-workload-model.md and checks every route the scripts call is published in
 * the REST/GraphQL contract, so a renamed endpoint fails CI instead of producing a 404 benchmark.
 */
class K6ExamScenariosContractTest {

    @Test
    void loginStormPinsLoginSloAndForbidsRateLimiting() throws Exception {
        String script = Files.readString(Path.of("load/k6/erg-login-storm.js"));

        assertThat(script)
                .contains("/api/v1/auth/login`")
                .contains("'p(95)<1500'", "'p(99)<3000'")
                .contains("login_rate_limited: ['count<1']");
    }

    @Test
    void examSessionPinsWriteSloAndSendsOnlyChangedAnswers() throws Exception {
        String script = Files.readString(Path.of("load/k6/erg-exam-session.js"));

        assertThat(script)
                .contains("/api/v1/lms/students/me/assignments")
                .contains("/api/v1/lms/attempts`")
                .contains("/api/v1/lms/attempts/${attemptId}/draft")
                .contains("/api/v1/lms/attempts/${attemptId}/submit")
                .contains("'http_req_duration{name:draft}': ['p(95)<800', 'p(99)<1500']")
                .contains("'http_req_duration{name:submit}': ['p(95)<800', 'p(99)<1500']")
                .contains("'http_req_failed{name:submit}': ['rate<0.001']")
                .doesNotContain("events:");
    }

    @Test
    void teacherProgressPinsReadSlo() throws Exception {
        String script = Files.readString(Path.of("load/k6/erg-teacher-progress.js"));

        assertThat(script)
                .contains("assignmentProgressWorkspace(input: $input)")
                .contains("'http_req_duration{name:progress}': ['p(95)<800']")
                .contains("graphql_errors: ['rate<0.01']");
    }

    @Test
    void fixtureCanRunTheExamAndTeacherScenarios() throws Exception {
        String sql = Files.readString(Path.of("load/fixtures/seed-postgres-scale.sql")).toLowerCase();

        // Without a published package, a gradable kind and a homeroom role, S3 cannot submit and S5 gets 403.
        assertThat(sql)
                .contains("insert into quiz_packages")
                .contains("'single_choice', 'multiple_choice', 1, true, 'auto'")
                .contains("'role_lms_homeroom_teacher'");
    }

    @Test
    void everyScenarioRouteIsPublishedInTheContract() throws Exception {
        String openApi = Files.readString(Path.of("contracts/openapi/erg-openapi.json"));
        String graphQl = Files.readString(Path.of("src/main/resources/graphql/schema.graphqls"));

        assertThat(openApi).contains(
                "\"/api/v1/auth/login\"",
                "\"/api/v1/lms/students/me/assignments\"",
                "\"/api/v1/lms/attempts\"",
                "\"/api/v1/lms/attempts/{attemptId}/draft\"",
                "\"/api/v1/lms/attempts/{attemptId}/submit\"");
        assertThat(graphQl).contains(
                "assignmentProgressWorkspace(input: AssignmentProgressWorkspaceInput!): AssignmentProgressWorkspace!");
    }
}

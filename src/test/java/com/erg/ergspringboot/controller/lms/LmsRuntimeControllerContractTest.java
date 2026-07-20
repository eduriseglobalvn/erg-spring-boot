package com.erg.ergspringboot.controller.lms;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.DockerClientFactory;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("postgresFixtureAvailable")
class LmsRuntimeControllerContractTest {

    static boolean postgresFixtureAvailable() {
        String configured = System.getProperty("erg.test.postgres.url");
        if (configured == null || configured.isBlank()) configured = System.getenv("ERG_TEST_POSTGRES_URL");
        if (configured != null && !configured.isBlank()) return true;
        try { return DockerClientFactory.instance().isDockerAvailable(); }
        catch (RuntimeException unavailable) { return false; }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void lmsHotPathUsesVersionedEnvelopeAndKeepsAttemptLifecycleIdempotent() throws Exception {
        mockMvc.perform(get("/api/v1/lms/students/me/assignments")
                        .header("X-User-ID", "usr_stu_ba_0001")
                        .header("X-Permissions", "*")
                        .header("X-Request-ID", "req-lms-list"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "req-lms-list"))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.items[0].id").value("asg_ba_math_g6_001"))
                .andExpect(jsonPath("$.path").value("/api/v1/lms/students/me/assignments"));

        String startJson = mockMvc.perform(post("/api/v1/lms/attempts")
                        .contentType("application/json")
                        .header("X-User-ID", "usr_stu_ba_0001")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "assignmentId": "asg_ba_math_g6_001",
                                  "quizId": "qz_math_g6_base",
                                  "packageId": "pkg_math_g6",
                                  "packageHash": "pkg_math_g6_v1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.status").value("in_progress"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String attemptId = extractString(startJson, "\"id\":\"");

        mockMvc.perform(put("/api/v1/lms/attempts/{attemptId}/answers/{questionId}", attemptId, "que_math_g6_001")
                        .contentType("application/json")
                        .header("X-User-ID", "usr_stu_ba_0001")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "answer": "A",
                                  "clientResult": {"source": "web"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(true))
                .andExpect(jsonPath("$.data.questionId").value("que_math_g6_001"));

        mockMvc.perform(post("/api/v1/lms/attempts/{attemptId}/sync", attemptId)
                        .contentType("application/json")
                        .header("X-User-ID", "usr_stu_ba_0001")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "packageHash": "wrong-hash",
                                  "quizVersion": "1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("conflict"))
                .andExpect(jsonPath("$.data.conflicts[0]").value("package_hash"));

        mockMvc.perform(post("/api/v1/lms/attempts/{attemptId}/submit", attemptId)
                        .contentType("application/json")
                        .header("X-User-ID", "usr_stu_ba_0001")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "answers": {"que_math_g6_002": "B"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(greaterThanOrEqualTo(2.0)))
                .andExpect(jsonPath("$.data.passed").value(true));

        mockMvc.perform(post("/api/v1/lms/attempts/{attemptId}/submit", attemptId)
                        .contentType("application/json")
                        .header("X-User-ID", "usr_stu_ba_0001")
                        .header("X-Permissions", "*")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true));

        mockMvc.perform(patch("/api/v1/lms/attempts/{attemptId}/draft", attemptId)
                        .contentType("application/json")
                        .header("X-User-ID", "usr_stu_ba_0001")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "answers": {"que_math_g6_001": "C"}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").value("BAD_REQUEST"));
    }

    @Test
    void unusedLmsPlaceholderSurfaceIsRemoved() throws Exception {
        mockMvc.perform(get("/api/v1/lms/dashboard/overview")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/lms/subjects")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/lms/imports/google-sheet/preview")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "spreadsheetId": "sheet-1",
                                  "tab": "students"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void lmsReadsOnlyReturnManagedEducationUnitsAndClasses() throws Exception {
        mockMvc.perform(get("/api/v1/lms/classes")
                        .header("X-User-ID", "teacher")
                        .header("X-Permissions", "lms.class.read")
                        .header("X-Managed-EducationUnit-IDs", "sch_binh_an")
                        .header("X-Managed-Class-IDs", "cls_ba_2025_6a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value("cls_ba_2025_6a1"))
                .andExpect(jsonPath("$.data.items[0].schoolId").value("sch_binh_an"));

        mockMvc.perform(get("/api/v1/lms/students/me/assignments")
                        .header("X-User-ID", "usr_stu_cl_0001")
                        .header("X-Permissions", "lms.assignment.read_self")
                        .header("X-Managed-EducationUnit-IDs", "sch_binh_an")
                        .header("X-Managed-Class-IDs", "cls_ba_2025_6a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void lmsEducationUnitsAliasReturnsOnlyManagedEducationUnits() throws Exception {
        mockMvc.perform(get("/api/v1/lms/education-units")
                        .header("X-User-ID", "teacher")
                        .header("X-Permissions", "lms.class.read")
                        .header("X-Managed-EducationUnit-IDs", "sch_binh_an"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value("sch_binh_an"));
    }

    private String extractString(String json, String marker) {
        int markerIndex = json.indexOf(marker);
        int valueStart = markerIndex + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        return json.substring(valueStart, valueEnd);
    }
}

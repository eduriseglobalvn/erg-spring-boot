package com.erg.ergspringboot.controller.lcms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.DockerClientFactory;

/**
 * The admin quiz bank's "Cài đặt gói" over HTTP: it saves the draft with the docVersion it read (a stale one is a
 * 409 the editor's conflict dialog handles), then renames / re-kinds the quiz through PATCH.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("postgresFixtureAvailable")
class LcmsQuizPackageSettingsHttpContractTest {

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
    void packageSettingsSaveTheDraftThenRenameTheQuiz() throws Exception {
        String created = mockMvc.perform(as(post("/api/v1/lcms/quizzes"))
                        .content("{\"title\":\"HTTP contract quiz\",\"kind\":\"train\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String quizId = extract(created, "\"quizId\":\"");
        long docVersion = Long.parseLong(extractNumber(created, "\"docVersion\":"));

        String draft = """
                {"docVersion":%d,
                 "settings":{"settings":{"enableTimeLimit":true,"timeLimit":"15:00","passingRate":60}},
                 "playerTemplate":{"accentColor":"#0071e3"},
                 "tree":[{"id":"g1","slides":[{"id":"s1","kind":"multiple-response","questionSetSettings":{"points":7},"options":{"acceptPartial":true}}]}]}
                """;
        mockMvc.perform(as(put("/api/v1/lcms/quizzes/{quizId}/draft", quizId)).content(draft.formatted(docVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.docVersion").value(docVersion + 1));
        // A second save from the same (now stale) read must not overwrite silently.
        mockMvc.perform(as(put("/api/v1/lcms/quizzes/{quizId}/draft", quizId)).content(draft.formatted(docVersion)))
                .andExpect(status().isConflict());

        mockMvc.perform(as(patch("/api/v1/lcms/quizzes/{quizId}", quizId)).content("{\"title\":\"  Renamed  \",\"kind\":\"TEST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizId").value(quizId))
                .andExpect(jsonPath("$.data.title").value("Renamed"))
                .andExpect(jsonPath("$.data.kind").value("test"));
        mockMvc.perform(as(patch("/api/v1/lcms/quizzes/{quizId}", quizId)).content("{\"kind\":\"exam\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(patch("/api/v1/lcms/quizzes/{quizId}", quizId)).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(patch("/api/v1/lcms/quizzes/{quizId}", "qz_does_not_exist")).content("{\"title\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
        return request.contentType("application/json")
                .header("X-Tenant-ID", "erg")
                .header("X-User-ID", "usr_stu_ba_0001")
                .header("X-Roles", "system.super_admin")
                .header("X-Permissions", "*");
    }

    private static String extract(String json, String marker) {
        int start = json.indexOf(marker) + marker.length();
        return json.substring(start, json.indexOf('"', start));
    }

    private static String extractNumber(String json, String marker) {
        int start = json.indexOf(marker) + marker.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        return json.substring(start, end);
    }
}

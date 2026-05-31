package com.erg.ergspringboot.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class RemainingModulesContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void notificationsAnalyticsOperationsAndAiRoutesUseVersionedEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/send")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "channel": "email",
                                  "recipient": "user@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.module").value("notifications"))
                .andExpect(jsonPath("$.data.status").value("queued"));

        mockMvc.perform(put("/api/v1/notifications/preferences")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "email": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operation").value("preferences"));

        mockMvc.perform(post("/api/v1/insight/session/begin")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "anonymousId": "anon-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.module").value("analytics"));

        mockMvc.perform(get("/api/v1/operations/system-status")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.module").value("operations"))
                .andExpect(jsonPath("$.data.healthy").value(true));

        mockMvc.perform(post("/api/v1/ai-content/keys")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "provider": "gemini",
                                  "secret": "super-secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.masked").value(true))
                .andExpect(jsonPath("$.data.secret").doesNotExist());
    }

    @Test
    void crawlerRssTrendingAndBotRoutesCoverNewAndLegacySurfaces() throws Exception {
        mockMvc.perform(post("/api/v1/crawler/crawl")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "url": "https://example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.module").value("crawler"))
                .andExpect(jsonPath("$.data.retryPolicy.maxAttempts").value(3));

        mockMvc.perform(get("/api/v1/crawler/stream")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event: progress")));

        mockMvc.perform(post("/api/crawler/rss/preview")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "url": "https://example.com/feed.xml"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.module").value("rss"));

        mockMvc.perform(post("/api/v1/rss/feeds")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "url": "https://example.com/feed.xml"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.module").value("rss"));

        mockMvc.perform(get("/api/v1/trending/topics/java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topic").value("java"));

        mockMvc.perform(post("/webhooks/telegram")
                        .contentType("application/json")
                        .content("""
                                {
                                  "update_id": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.module").value("bot"));
    }

    @Test
    void seoSitemapRecruitmentReviewsCommunityAndDisclosureRoutesAreMounted() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sitemap: /sitemap.xml")));

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<urlset")));

        mockMvc.perform(post("/api/v1/seo/keywords")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "keyword": "spring boot"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.module").value("seo"));

        mockMvc.perform(post("/api/v1/recruitment/apply")
                        .contentType("application/json")
                        .content("""
                                {
                                  "jobId": "job-1",
                                  "email": "candidate@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.module").value("recruitment"));

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType("application/json")
                        .content("""
                                {
                                  "rating": 5,
                                  "content": "Great"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.module").value("reviews"));

        mockMvc.perform(post("/api/v1/hoclieu/community/topics")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "title": "Math"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.module").value("community"));

        mockMvc.perform(post("/api/v1/public-disclosure")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "title": "Annual report"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.module").value("public-disclosure"));
    }

    @Test
    void migrationPlatformAndPolicyEndpointsExposeCutoverArtifacts() throws Exception {
        mockMvc.perform(get("/api/v1/migration/data-parity-matrix")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.module").value("data-migration"))
                .andExpect(jsonPath("$.data.items[0].status").value("ready"));

        mockMvc.perform(get("/api/v1/migration/native-image-assessment")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultProductionRuntime").value("JVM"));

        mockMvc.perform(get("/api/v1/platform/tenancy")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultTenant").value("erg"))
                .andExpect(jsonPath("$.data.redisPrefix").value("erg:{tenant}:"));

        mockMvc.perform(post("/api/v1/migration/load-test/report")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "scenario": "lms-submit-1000",
                                  "p99": 2500
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", notNullValue()));
    }
}

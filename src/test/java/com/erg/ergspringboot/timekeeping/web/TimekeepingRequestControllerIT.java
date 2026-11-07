package com.erg.ergspringboot.timekeeping.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.notifications.NotificationAdminOperations;
import com.erg.ergspringboot.timekeeping.internal.request.RequestServiceIT.Fixture;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** HTTP contract of /api/v1/timekeeping/requests on real PostgreSQL (clock Thu 12/11/2026 08:00 VN). */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIf("com.erg.ergspringboot.timekeeping.web.TimekeepingSelfControllerIT#postgresAvailable")
class TimekeepingRequestControllerIT {

    private static final String TENANT = "tk-req-web";
    private static final String REQUESTS = "/api/v1/timekeeping/requests";
    private static final String LEAVE = """
            {"type":"LEAVE","leaveKind":"ANNUAL","fromDate":"2026-11-16","toDate":"2026-11-17","part":"FULL",
             "note":"Việc gia đình","days":99}""";

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedTimekeepingClock() {
            return Clock.fixed(Instant.parse("2026-11-12T01:00:00Z"), ZoneOffset.UTC);
        }
    }

    @MockitoBean CalendarReadPort calendar;
    @MockitoBean NotificationAdminOperations notifications;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private Fixture f;

    @BeforeEach
    void seed() {
        when(calendar.teacherPeriods(anyString(), anyString(), any(), any())).thenReturn(List.of());
        f = Fixture.seed(jdbc, TENANT, "web");
    }

    @Test
    void studentTokenIsForbidden() throws Exception {
        mvc.perform(as(get(REQUESTS + "/mine"), "usr_rq_student", "lms.learner.read")).andExpect(status().isForbidden());
        mvc.perform(as(post(REQUESTS).contentType(MediaType.APPLICATION_JSON).content(LEAVE), "usr_rq_student",
                "lms.learner.read")).andExpect(status().isForbidden());
    }

    @Test
    void teacherCreatesSubmitsAndSeesTheRequestInMine() throws Exception {
        String body = mvc.perform(as(post(REQUESTS).contentType(MediaType.APPLICATION_JSON).content(LEAVE), f.teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.code").value(matchesPattern("DT-2026-\\d{4}")))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.data.id");

        mvc.perform(as(post(REQUESTS + "/" + id + "/submit"), f.teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.days").value(2.0)) // counted by the server, the client's 99 is ignored
                .andExpect(jsonPath("$.data.currentStep").value(1))
                .andExpect(jsonPath("$.data.steps[0].approverId").value(f.lead()))
                .andExpect(jsonPath("$.data.steps[0].approverName").value("Tên " + f.lead()));

        mvc.perform(as(get(REQUESTS + "/mine").param("status", "PENDING"), f.teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(id));
        mvc.perform(as(get(REQUESTS + "/" + id), f.teacher())).andExpect(status().isOk());
        mvc.perform(as(get(REQUESTS + "/preview-leave").param("from", "2026-11-16").param("to", "2026-11-16")
                .param("kind", "ANNUAL"), f.teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(1))
                .andExpect(jsonPath("$.data.balanceBefore").value(12.0)) // 14 - 2 pending
                .andExpect(jsonPath("$.data.balanceAfter").value(11.0));
    }

    @Test
    void anotherTeachersRequestIsNotFoundAndBadInputIsRejected() throws Exception {
        String body = mvc.perform(as(post(REQUESTS).contentType(MediaType.APPLICATION_JSON).content(LEAVE), f.teacher()))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.data.id");

        mvc.perform(as(get(REQUESTS + "/" + id), f.lead())).andExpect(status().isNotFound());
        mvc.perform(as(post(REQUESTS + "/" + id + "/cancel"), f.lead())).andExpect(status().isNotFound());
        mvc.perform(as(post(REQUESTS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"OVERTIME\",\"fromDate\":\"2026-11-16\",\"toDate\":\"2026-11-16\"}"), f.teacher()))
                .andExpect(status().isBadRequest());
        mvc.perform(as(post(REQUESTS).contentType(MediaType.APPLICATION_JSON)
                .content(LEAVE.replace("2026-11-16", "2026-11-18")), f.teacher()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ngày kết thúc phải sau ngày bắt đầu"));
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String user) {
        return as(request, user, "timekeeping.self");
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String user, String perms) {
        return request.header("X-Tenant-ID", TENANT).header("X-User-ID", user)
                .header("X-Roles", "lms.instructor").header("X-Permissions", perms);
    }
}

package com.erg.ergspringboot.timekeeping.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.calendar.TeacherPeriod;
import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.timekeeping.internal.evidence.LocalPrivateObjectStorage;
import com.erg.ergspringboot.timekeeping.internal.punch.PunchCodeGenerator;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShift;
import com.erg.ergspringboot.timekeeping.persistence.TkShiftRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;

/** HTTP contract of /api/v1/timekeeping/me on real PostgreSQL with a fixed server clock (Thu 12/11/2026 07:40). */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIf("postgresAvailable")
class TimekeepingSelfControllerIT {

    private static final Instant NOW = Instant.parse("2026-11-12T00:40:00Z");
    private static final String TENANT = "tk-it";
    private static final String TEACHER = "usr_tk_teacher";
    private static final String SCHOOL = "sch_tk_it";
    private static final String ME = "/api/v1/timekeeping/me";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    private static final Path PHOTO_ROOT = tempRoot();

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
        registry.add("erg.timekeeping.photos.local-root", PHOTO_ROOT::toString);
    }

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedTimekeepingClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @MockitoBean CalendarReadPort calendar;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired LocalPrivateObjectStorage localPhotos;
    @Autowired PunchCodeGenerator codes;
    @Autowired TransactionTemplate transactions;
    @PersistenceContext EntityManager entityManager;
    @Autowired TkShiftRepository shifts;
    @Autowired TkPunchRepository punchRepository;

    @BeforeEach
    void seed() {
        if (!TestTransaction.isActive()) return; // the counter race test commits; it must not commit fixtures
        jdbc.update("insert into education_units (id, tenant_id, name, slug, type, status, created_at, updated_at)"
                + " values (?, ?, 'Trường TK', 'truong-tk', 'school', 'active', now(), now()) on conflict do nothing",
                SCHOOL, TENANT);
        jdbc.update("insert into users (id, tenant_id, email, full_name, status, created_at, updated_at)"
                + " values (?, ?, 'tk.teacher@example.test', 'Cô Lan', 'active', now(), now()) on conflict do nothing",
                TEACHER, TENANT);
        jdbc.update("insert into user_access_scopes (id, user_id, education_unit_id, role, created_at, updated_at)"
                + " values ('scope_tk_it', ?, ?, 'lms.instructor', now(), now()) on conflict do nothing", TEACHER, SCHOOL);
        jdbc.update("insert into tk_shifts (id, tenant_id, school_id, name, start_time, end_time, work_units, weekdays,"
                + " created_at, updated_at) values ('shift-tk-it', ?, ?, 'Ca sáng', '07:30', '11:30', 1.0,"
                + " '{1,2,3,4,5}', '2020-01-01', now())", TENANT, SCHOOL);
        jdbc.update("insert into tk_sites (id, tenant_id, school_id, name, lat, lng, radius_m, active, created_at,"
                + " updated_at) values ('site-tk-it', ?, ?, 'Cơ sở Cầu Giấy', 21.0285, 105.8542, 150, true, now(), now())",
                TENANT, SCHOOL);
        jdbc.update("insert into tk_face_consents (id, tenant_id, user_id, consented_at, created_at, updated_at)"
                + " values ('consent-tk-it', ?, ?, now(), now(), now())", TENANT, TEACHER);
        when(calendar.teacherPeriods(eq(TENANT), eq(TEACHER), any(), any())).thenReturn(List.of(new TeacherPeriod(
                Instant.parse("2026-11-12T01:00:00Z"), Instant.parse("2026-11-12T01:45:00Z"), "7A", "Toán", "P201")));
    }

    @Test
    void studentTokenIsForbidden() throws Exception {
        mvc.perform(as(get(ME + "/today"), "usr_tk_student", "lms.learner.read")).andExpect(status().isForbidden());
    }

    @Test
    void teacherTodayHasTheShiftSitesConsentAndPeriods() throws Exception {
        mvc.perform(teacher(get(ME + "/today")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-11-12"))
                .andExpect(jsonPath("$.data.shift.name").value("Ca sáng"))
                .andExpect(jsonPath("$.data.sites[0].name").value("Cơ sở Cầu Giấy"))
                .andExpect(jsonPath("$.data.sites[0].radiusM").value(150))
                .andExpect(jsonPath("$.data.requireFace").value(true))
                .andExpect(jsonPath("$.data.faceConsented").value(true))
                .andExpect(jsonPath("$.data.periods[0].className").value("7A"))
                .andExpect(jsonPath("$.data.periods[0].room").value("P201"));
        mvc.perform(teacher(get(ME + "/clock")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serverTime").value(containsString("2026-11-12T00:40")));
    }

    @Test
    void punchUsesTheServerClockAndIgnoresAClientTime() throws Exception {
        mvc.perform(punch("IN", "key-1").param("clientTime", "2020-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verdict").value("VALID"))
                .andExpect(jsonPath("$.data.punch.code").value("CC-121126-0001"))
                .andExpect(jsonPath("$.data.punch.method").value("GPS_FACE"))
                .andExpect(jsonPath("$.data.minutesVsShift").value(10))
                .andExpect(jsonPath("$.data.nextPeriod.className").value("7A"));

        Timestamp stored = jdbc.queryForObject(
                "select server_time from tk_punches where tenant_id = ? and idempotency_key = 'key-1'",
                Timestamp.class, TENANT);
        assertThat(stored.toInstant()).isEqualTo(NOW);
        entityManager.flush(); // the day row is written at commit; the test transaction never commits
        assertThat(jdbc.queryForMap("select status, work_units, late_minutes, in_punch_id from tk_days"
                + " where tenant_id = ? and user_id = ?", TENANT, TEACHER))
                .containsEntry("status", "LATE_EARLY")
                .containsEntry("late_minutes", 10)
                .satisfies(row -> assertThat(row.get("in_punch_id")).isNotNull());
    }

    @Test
    void sameKeyIsOneRowAnotherInIsAlreadyPunchedAndCodesCountUp() throws Exception {
        mvc.perform(punch("IN", "key-1")).andExpect(jsonPath("$.data.punch.code").value("CC-121126-0001"));
        mvc.perform(punch("IN", "key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.punch.code").value("CC-121126-0001"));
        mvc.perform(punch("IN", "key-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyPunched").value(true))
                .andExpect(jsonPath("$.data.punch.code").value("CC-121126-0001"));
        mvc.perform(punch("OUT", "key-3")).andExpect(jsonPath("$.data.punch.code").value("CC-121126-0002"));
        mvc.perform(punch("OUT", "key-4")).andExpect(jsonPath("$.data.punch.code").value("CC-121126-0003"));

        entityManager.flush();
        assertThat(count("select count(*) from tk_punches where tenant_id = ? and user_id = ?")).isEqualTo(3);
        assertThat(jdbc.queryForObject("select p.code from tk_days d join tk_punches p on p.id = d.out_punch_id"
                + " where d.tenant_id = ? and d.user_id = ?", String.class, TENANT, TEACHER))
                .isEqualTo("CC-121126-0003");
    }

    @Test
    void missingKeyAndImpossibleCoordinatesAreBadRequests() throws Exception {
        MockMultipartHttpServletRequestBuilder noKey = multipart(ME + "/punches");
        noKey.file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", JPEG)).param("direction", "IN")
                .param("lat", "21.0285").param("lng", "105.8542").param("accuracyM", "20");
        mvc.perform(teacher(noKey)).andExpect(status().isBadRequest());
        mvc.perform(punch("IN", "key-1", "95")).andExpect(status().isBadRequest());
        mvc.perform(punch("IN", "k".repeat(101))).andExpect(status().isBadRequest());
        assertThat(count("select count(*) from tk_punches where tenant_id = ? and user_id = ?")).isZero();
    }

    @Test
    void lockedPeriodIsAConflict() throws Exception {
        jdbc.update("insert into tk_period_locks (id, tenant_id, school_id, period, locked_at, locked_by, created_at,"
                + " updated_at) values ('lock-tk-it', ?, ?, '2026-11', now(), 'admin', now(), now())", TENANT, SCHOOL);

        mvc.perform(punch("IN", "key-1"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Kỳ công đã chốt")));
    }

    @Test
    void remoteRuleStoresNoLocation() throws Exception {
        jdbc.update("insert into tk_remote_rules (id, tenant_id, school_id, kind, user_ids, active, created_at,"
                + " updated_at) values ('remote-tk-it', ?, ?, 'USERS', ?::jsonb, true, now(), now())",
                TENANT, SCHOOL, "[\"" + TEACHER + "\"]");

        mvc.perform(punch("IN", "key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.punch.remote").value(true))
                .andExpect(jsonPath("$.data.punch.method").value("REMOTE_FACE"));
        assertThat(jdbc.queryForMap("select lat, lng, distance_m, site_id from tk_punches where tenant_id = ?", TENANT))
                .allSatisfy((column, value) -> assertThat(value).as(column).isNull());
    }

    @Test
    void dayShowsASignedPhotoThatServesPrivatelyThenPurged() throws Exception {
        mvc.perform(punch("IN", "key-1")).andExpect(status().isOk());

        String body = mvc.perform(teacher(get(ME + "/days/2026-11-12")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.in.photoUrl").value(startsWith("/api/v1/timekeeping/photos/file?key=")))
                .andExpect(jsonPath("$.data.in.photoPurged").value(false))
                .andExpect(jsonPath("$.data.periods[0].subject").value("Toán"))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.data.in.photoUrl");

        mvc.perform(get(java.net.URI.create(url)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(content().bytes(JPEG));

        jdbc.update("update tk_punches set photo_key = null, photo_purged_at = now() where tenant_id = ?", TENANT);
        entityManager.clear(); // the purge ran outside this persistence context
        mvc.perform(teacher(get(ME + "/days/2026-11-12")))
                .andExpect(jsonPath("$.data.in.photoPurged").value(true))
                .andExpect(jsonPath("$.data.in.photoUrl").doesNotExist());
    }

    @Test
    void photoFileRejectsTamperedTraversalAndMissing() throws Exception {
        String key = "private/timekeeping/tk-it/2026/11/" + UUID.randomUUID() + ".jpg";
        localPhotos.put(key, JPEG, "image/jpeg");
        String good = localPhotos.presignGet(key, Duration.ofMinutes(5)).toString();

        mvc.perform(get(java.net.URI.create(good))).andExpect(status().isOk());
        mvc.perform(get(java.net.URI.create(good.replaceAll("sig=[0-9a-f]+", "sig=" + "0".repeat(64))))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/timekeeping/photos/file").param("key", "private/timekeeping/../../etc/passwd")
                .param("exp", String.valueOf(NOW.getEpochSecond() + 60)).param("sig", "00"))
                .andExpect(status().isForbidden());
        String missing = localPhotos.presignGet("private/timekeeping/tk-it/2026/11/missing.jpg", Duration.ofMinutes(5))
                .toString();
        mvc.perform(get(java.net.URI.create(missing))).andExpect(status().isNotFound());
    }

    @Test
    void monthSummarisesDaysAgainstTheStandardAndTheLockDay() throws Exception {
        mvc.perform(punch("IN", "key-1")).andExpect(status().isOk());

        mvc.perform(teacher(get(ME + "/month").param("period", "2026-11")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.standardUnits").value(21.0))
                .andExpect(jsonPath("$.data.actualUnits").value(1.0))
                .andExpect(jsonPath("$.data.lateCount").value(1))
                .andExpect(jsonPath("$.data.lateMinutes").value(10))
                .andExpect(jsonPath("$.data.annualLeaveRemaining").value(12))
                .andExpect(jsonPath("$.data.pendingRequests").value(0))
                .andExpect(jsonPath("$.data.periods").value(1))
                .andExpect(jsonPath("$.data.reviewUntil").value("2026-12-08"))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-11-12"))
                .andExpect(jsonPath("$.data.days[0].status").value("LATE_EARLY"));
        mvc.perform(teacher(get(ME + "/month").param("period", "2026-13"))).andExpect(status().isBadRequest());
    }

    @Test
    void faceConsentIsRecordedOnce() throws Exception {
        mvc.perform(as(post(ME + "/face-consent"), "usr_tk_other", "timekeeping.self")).andExpect(status().isOk());
        mvc.perform(as(post(ME + "/face-consent"), "usr_tk_other", "timekeeping.self")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from tk_face_consents where tenant_id = ? and user_id = ?",
                Integer.class, TENANT, "usr_tk_other")).isEqualTo(1);
    }

    @Test
    void reminderQueriesFindTheShiftEndingSoonAndWhoHasNotPunchedOut() throws Exception {
        LocalDate day = LocalDate.of(2026, 11, 12);
        assertThat(shifts.findEndingBetween(4, LocalTime.of(11, 20), LocalTime.of(11, 30)))
                .extracting(TkShift::getId).contains("shift-tk-it");
        assertThat(shifts.findEndingBetween(6, LocalTime.of(11, 20), LocalTime.of(11, 30)))
                .extracting(TkShift::getId).doesNotContain("shift-tk-it");
        assertThat(shifts.findEndingBetween(4, LocalTime.of(11, 30), LocalTime.of(11, 40)))
                .extracting(TkShift::getId).doesNotContain("shift-tk-it");

        mvc.perform(punch("IN", "key-1")).andExpect(status().isOk());
        assertThat(punchRepository.findUsersInWithoutOut(TENANT, "shift-tk-it", day)).containsExactly(TEACHER);
        mvc.perform(punch("OUT", "key-2")).andExpect(status().isOk());
        assertThat(punchRepository.findUsersInWithoutOut(TENANT, "shift-tk-it", day)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCodesForOneTenantAndDayAreDistinctAndGapless() throws Exception {
        String tenant = "tk-race-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate day = LocalDate.of(2026, 11, 12);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                futures.add(pool.submit(() -> transactions.execute(status -> codes.next(tenant, day))));
            }
            Set<String> issued = new HashSet<>();
            for (Future<String> future : futures) issued.add(future.get());
            assertThat(issued).hasSize(40).contains("CC-121126-0001", "CC-121126-0040");
        } finally {
            pool.shutdownNow();
            jdbc.update("delete from tk_code_counters where tenant_id = ?", tenant);
        }
    }

    private MockMultipartHttpServletRequestBuilder punch(String direction, String key) {
        return punch(direction, key, "21.0285");
    }

    private MockMultipartHttpServletRequestBuilder punch(String direction, String key, String lat) {
        MockMultipartHttpServletRequestBuilder request = multipart(ME + "/punches");
        request.file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", JPEG));
        return teacher(request.header("Idempotency-Key", key).param("direction", direction)
                .param("lat", lat).param("lng", "105.8542").param("accuracyM", "20"));
    }

    private Integer count(String sql) {
        return jdbc.queryForObject(sql, Integer.class, TENANT, TEACHER);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B teacher(B request) {
        return as(request, TEACHER, "timekeeping.self");
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String user, String perms) {
        return request.header("X-Tenant-ID", TENANT).header("X-User-ID", user)
                .header("X-Roles", "lms.instructor").header("X-Permissions", perms);
    }

    static boolean postgresAvailable() {
        String url = System.getProperty("erg.test.postgres.url", System.getenv("ERG_TEST_POSTGRES_URL"));
        if (url != null && !url.isBlank()) return true;
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private static Path tempRoot() {
        try {
            return Files.createTempDirectory("tk-photos-it");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

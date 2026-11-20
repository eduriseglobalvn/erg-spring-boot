package com.erg.ergspringboot.lms.internal.game;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersion;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionQuestion;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/** Create and join a game room over HTTP: permissions, class membership, filtered kinds and the wrong-code limit. */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIf("postgresAvailable")
class GameRoomHttpIntegrationTest {

    private static final String TENANT = "tenant-games-it";

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    static boolean postgresAvailable() {
        String configured = System.getProperty("erg.test.postgres.url");
        if (configured == null || configured.isBlank()) configured = System.getenv("ERG_TEST_POSTGRES_URL");
        if (configured != null && !configured.isBlank()) return true;
        try { return DockerClientFactory.instance().isDockerAvailable(); }
        catch (RuntimeException unavailable) { return false; }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String userId, String permission) {
        return request.header("X-Tenant-ID", TENANT).header("X-User-ID", userId)
                .header("X-Roles", permission.equals("*") ? "system.super_admin" : "tester").header("X-Permissions", permission).contentType("application/json");
    }

    @Test
    void teacherCreatesRoomStudentsOfTheClassJoinOthersAndWrongCodesAreRejected() throws Exception {
        seed();
        mockMvc.perform(as(get("/api/v1/lms/games/sources").param("classId", "class-games"), "usr-games-teacher", "*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quizVersionId").value("qv-games"))
                .andExpect(jsonPath("$.data[0].questionCount").value(3))
                .andExpect(jsonPath("$.data[0].playableCount").value(2))
                .andExpect(jsonPath("$.data[0].filteredCount").value(1))
                .andExpect(jsonPath("$.data[0].filteredByKind.essay").value(1));

        // A student has no host permission.
        mockMvc.perform(as(post("/api/v1/lms/games/rooms"), "usr-games-a", "lms.games.play")
                        .content(create(10)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/v1/lms/games/rooms"), "usr-games-teacher", "*").content(create(4)))
                .andExpect(status().isBadRequest());

        String body = mockMvc.perform(as(post("/api/v1/lms/games/rooms"), "usr-games-teacher", "*").content(create(10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOBBY"))
                .andExpect(jsonPath("$.data.gameType").value("HACK"))
                .andExpect(jsonPath("$.data.questionCount").value(2))
                .andExpect(jsonPath("$.data.rules.allowHack").value(false))
                .andExpect(jsonPath("$.data.code", matchesPattern("\\d{4}")))
                .andReturn().getResponse().getContentAsString();
        String code = body.replaceAll(".*\"code\":\"(\\d{4})\".*", "$1");
        String roomId = body.replaceAll(".*\"id\":\"([^\"]+)\",\"gameType\".*", "$1");

        mockMvc.perform(as(post("/api/v1/lms/games/rooms/join"), "usr-games-a", "lms.games.play")
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomId").value(roomId));
        mockMvc.perform(as(get("/api/v1/lms/games/rooms/{id}", roomId), "usr-games-a", "lms.games.play"))
                .andExpect(status().isOk());
        // Not a student of the room's class.
        mockMvc.perform(as(post("/api/v1/lms/games/rooms/join"), "usr-games-outsider", "lms.games.play")
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/v1/lms/games/rooms/{id}", roomId), "usr-games-outsider", "lms.games.play"))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/v1/lms/games/rooms"), "usr-games-teacher", "*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + roomId + "')]", hasSize(1)));

        String wrong = code.equals("0000") ? "0001" : "0000";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(as(post("/api/v1/lms/games/rooms/join"), "usr-games-b", "lms.games.play")
                            .content("{\"code\":\"" + wrong + "\"}"))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(as(post("/api/v1/lms/games/rooms/join"), "usr-games-b", "lms.games.play")
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void classWrongRoomsLastGameAndAccuracyComeFromEarlierEndedRooms() throws Exception {
        seed();
        mockMvc.perform(as(get("/api/v1/lms/games/sources").param("classId", "class-games"), "usr-games-teacher", "*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].classSize").value(2))
                .andExpect(jsonPath("$.data[0].classWrongCount").value(0))
                .andExpect(jsonPath("$.data[0].lastGame").doesNotExist());
        // No wrong answers yet: nothing to play.
        mockMvc.perform(as(post("/api/v1/lms/games/rooms"), "usr-games-teacher", "*").content(create(10, "CLASS_WRONG")))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(as(post("/api/v1/lms/games/rooms"), "usr-games-teacher", "*").content(create(10, "NOPE")))
                .andExpect(status().isBadRequest());

        // An earlier ended room: q0 wrong then right, q1 right (2 of 3 correct); a wrong essay-like id is ignored.
        entityManager.createNativeQuery("""
                insert into game_room (id, tenant_id, game_type, join_code, quiz_version_id, class_id, host_user_id,
                    status, duration_minutes, question_count, created_at, ended_at)
                values ('gr-old', :t, 'HACK', '1234', 'qv-games', 'class-games', 'usr-games-teacher', 'ENDED', 10, 2,
                    now() - interval '2 hours', now() - interval '1 hour')
                """).setParameter("t", TENANT).executeUpdate();
        entityManager.createNativeQuery("""
                insert into game_answer (room_id, user_id, question_id, attempt_no, context, correct, answered_ms)
                values ('gr-old', 'usr-games-a', 'q0', 1, 'play', false, 900),
                       ('gr-old', 'usr-games-a', 'q0', 2, 'play', true, 900),
                       ('gr-old', 'usr-games-b', 'q1', 1, 'play', true, 900)
                """).executeUpdate();

        mockMvc.perform(as(get("/api/v1/lms/games/sources").param("classId", "class-games"), "usr-games-teacher", "*"))
                .andExpect(jsonPath("$.data[0].classWrongCount").value(1))
                .andExpect(jsonPath("$.data[0].lastGame.roomId").value("gr-old"))
                .andExpect(jsonPath("$.data[0].lastGame.gameType").value("HACK"))
                .andExpect(jsonPath("$.data[0].lastGame.className").value("Games 6A"));
        // Exercise bank asks without a class: lastGame only, class fields null.
        mockMvc.perform(as(get("/api/v1/lms/games/sources"), "usr-games-teacher", "*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].lastGame.roomId").value("gr-old"))
                .andExpect(jsonPath("$.data[0].classSize").doesNotExist())
                .andExpect(jsonPath("$.data[0].classWrongCount").doesNotExist());
        mockMvc.perform(as(post("/api/v1/lms/games/rooms"), "usr-games-teacher", "*").content(create(10, "CLASS_WRONG")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionCount").value(1))
                .andExpect(jsonPath("$.data.questionSet").value("CLASS_WRONG"))
                .andExpect(jsonPath("$.data.quizId").value("qz-games"))
                .andExpect(jsonPath("$.data.quizVersionId").value("qv-games"))
                .andExpect(jsonPath("$.data.accuracy").doesNotExist());
        mockMvc.perform(as(get("/api/v1/lms/games/rooms"), "usr-games-teacher", "*"))
                .andExpect(jsonPath("$.data[?(@.id == 'gr-old')].accuracy", contains(closeTo(2.0 / 3, 0.001))))
                .andExpect(jsonPath("$.data[?(@.id == 'gr-old')].questionSet", contains("ALL")));
    }

    private static String create(int minutes) {
        return create(minutes, "ALL");
    }

    private static String create(int minutes, String questionSet) {
        return """
                {"gameType":"HACK","quizVersionId":"qv-games","classId":"class-games","durationMinutes":%d,"questionSet":"%s",
                 "rules":{"allowHack":true,"emergencyPatch":false,"showLeaderboard":true,"showSolution":true,
                          "allowLateJoin":true,"questionOrder":"RANDOM"}}
                """.formatted(minutes, questionSet);
    }

    private void seed() {
        entityManager.createNativeQuery("select set_config('app.tenant_id', :t, true)")
                .setParameter("t", TENANT).getSingleResult();
        EducationUnit school = new EducationUnit();
        school.setId("school-games"); school.setTenantId(TENANT); school.setName("Games School"); school.setType("school");
        entityManager.persist(school);
        for (String userId : List.of("usr-games-a", "usr-games-b", "usr-games-outsider", "usr-games-teacher")) {
            User user = new User();
            user.setId(userId); user.setTenantId(TENANT); user.setUsername(userId); user.setEmail(userId + "@example.test");
            user.setFullName(userId); user.setStatus("ACTIVE"); user.setMustChangePassword(false);
            user.setIsProfileCompleted(true); user.setRecoveryEmailVerified(false);
            entityManager.persist(user);
        }
        Classroom classroom = new Classroom();
        classroom.setId("class-games"); classroom.setTenantId(TENANT); classroom.setSchoolId("school-games");
        classroom.setName("Games 6A"); classroom.setGrade("6"); classroom.setAcademicYear("2026"); classroom.setStatus("active");
        entityManager.persist(classroom);
        for (String suffix : List.of("a", "b")) {
            Student student = new Student();
            student.setId("stu-games-" + suffix); student.setTenantId(TENANT); student.setSchoolId("school-games");
            student.setAcademicClassId("class-games"); student.setStudentCode("G-" + suffix); student.setFullName("HS " + suffix);
            student.setAuthUserId("usr-games-" + suffix); student.setStatus("active");
            entityManager.persist(student);
        }
        Quiz quiz = new Quiz();
        quiz.setId("qz-games"); quiz.setTenantId(TENANT); quiz.setTitle("Games quiz"); quiz.setKind("practice");
        quiz.setStatus("published"); quiz.setVersion(1);
        entityManager.persist(quiz);
        QuizVersion version = new QuizVersion();
        version.setId("qv-games"); version.setQuizId("qz-games"); version.setTenantId(TENANT); version.setVersionLabel("v1");
        version.setVersionNumber(1); version.setRuntimeQuiz(Map.of()); version.setAnswerKey(Map.of());
        version.setGradingMode("server-authoritative"); version.setSchemaVersion(1); version.setContentHash("hash-games");
        version.setPublishedAt(OffsetDateTime.now()); version.setIsActive(true);
        entityManager.persist(version);
        entityManager.flush();
        for (int i = 0; i < 3; i++) {
            boolean essay = i == 2;
            QuizVersionQuestion q = new QuizVersionQuestion();
            q.setQuizVersionId("qv-games"); q.setTenantId(TENANT); q.setRuntimeQuestionId("q" + i);
            q.setKind(essay ? "essay" : "single_choice"); q.setPoints(BigDecimal.ONE); q.setAutoGradable(!essay);
            q.setGradingPolicy(essay ? "manual" : "auto"); q.setOrderIndex(i);
            q.setBody(Map.of("id", "q" + i, "title", "Q" + i, "choices", List.of(Map.of("id", "a"), Map.of("id", "b")),
                    "feedback", Map.of("correct", "Well done", "incorrect", "It was A")));
            q.setAnswerKey(essay ? null : Map.of("correctChoiceId", "a"));
            entityManager.persist(q);
        }
        entityManager.flush();
    }
}

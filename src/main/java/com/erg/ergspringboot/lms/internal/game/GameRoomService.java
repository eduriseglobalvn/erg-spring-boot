package com.erg.ergspringboot.lms.internal.game;

import com.erg.ergspringboot.exception.ApiException;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizPackage;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersion;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionQuestion;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionQuestionRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionRepository;
import com.erg.ergspringboot.lms.internal.game.GameDtos.CreateRoomRequest;
import com.erg.ergspringboot.lms.internal.game.GameDtos.JoinRequest;
import com.erg.ergspringboot.lms.internal.game.GameDtos.JoinResponse;
import com.erg.ergspringboot.lms.internal.game.GameDtos.RoomSummary;
import com.erg.ergspringboot.lms.internal.game.GameDtos.RulesRequest;
import com.erg.ergspringboot.lms.internal.game.GameDtos.SourceItem;
import com.erg.ergspringboot.lms.internal.game.GameModel.Question;
import com.erg.ergspringboot.lms.internal.game.GameModel.Room;
import com.erg.ergspringboot.lms.internal.game.GameModel.Rules;
import com.erg.ergspringboot.lms.internal.game.GameModel.Status;
import com.erg.ergspringboot.lms.internal.quiz.runtime.QuizRuntimePackageService;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Creating and joining rooms. This is the only place that reads Postgres before a game: the quiz questions, answer
 * keys and solutions of the pinned version plus the class roster are loaded once into the {@link Room}.
 */
@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class GameRoomService {

    /** Kinds the game cannot grade on its own. */
    static final Set<String> FILTERED_KINDS = Set.of("essay", "likert_scale");
    private static final int MAX_JOIN_FAILURES = 5;
    private static final int ROSTER_LIMIT = 500;

    private final QuizVersionRepository versions;
    private final QuizVersionQuestionRepository questions;
    private final QuizRepository quizzes;
    private final QuizRuntimePackageService packages;
    private final ClassroomRepository classes;
    private final StudentRepository students;
    private final GameRoomManager manager;
    private final GameResultWriter writer;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    /** Wrong-code attempts per user; five misses lock joining for a minute. */
    private final Cache<String, AtomicInteger> joinFailures =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).maximumSize(50_000).build();

    public GameRoomService(QuizVersionRepository versions, QuizVersionQuestionRepository questions,
                           QuizRepository quizzes, QuizRuntimePackageService packages, ClassroomRepository classes,
                           StudentRepository students, GameRoomManager manager, GameResultWriter writer,
                           JdbcTemplate jdbc, ObjectMapper mapper) {
        this.versions = versions;
        this.questions = questions;
        this.quizzes = quizzes;
        this.packages = packages;
        this.classes = classes;
        this.students = students;
        this.manager = manager;
        this.writer = writer;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    // ---- sources --------------------------------------------------------------------------------------------

    public List<SourceItem> sources(UserAccessContext access, String classId) {
        requireClass(access, classId);
        record Row(String versionId, String quizId, String title, int number, Instant publishedAt, String kind, int count) { }
        List<Row> rows = jdbc.query("""
                select v.id as version_id, v.quiz_id, q.title, v.version_number, v.published_at, qq.kind, count(*) as n
                from quiz_versions v
                join lms_quizzes q on q.id = v.quiz_id and q.tenant_id = v.tenant_id
                join quiz_version_questions qq on qq.quiz_version_id = v.id
                where v.tenant_id = ? and v.is_active and v.published_at is not null and q.deleted_at is null
                group by v.id, v.quiz_id, q.title, v.version_number, v.published_at, qq.kind
                order by v.published_at desc
                """, (rs, i) -> new Row(rs.getString("version_id"), rs.getString("quiz_id"), rs.getString("title"),
                rs.getInt("version_number"), rs.getTimestamp("published_at").toInstant(), rs.getString("kind"),
                rs.getInt("n")), access.tenantId());
        Map<String, List<Row>> byVersion = new LinkedHashMap<>();
        rows.forEach(r -> byVersion.computeIfAbsent(r.versionId(), k -> new ArrayList<>()).add(r));
        List<SourceItem> items = new ArrayList<>();
        for (List<Row> group : byVersion.values()) {
            Row first = group.get(0);
            Map<String, Integer> filtered = new LinkedHashMap<>();
            int total = 0;
            int skipped = 0;
            for (Row r : group) {
                total += r.count();
                if (FILTERED_KINDS.contains(r.kind())) {
                    filtered.put(r.kind(), r.count());
                    skipped += r.count();
                }
            }
            items.add(new SourceItem(first.versionId(), first.quizId(), first.title(), first.number(),
                    first.publishedAt().toString(), total, total - skipped, skipped, filtered));
            if (items.size() >= 100) break;
        }
        return items;
    }

    // ---- create ---------------------------------------------------------------------------------------------

    public RoomSummary create(UserAccessContext access, CreateRoomRequest request) {
        if (request == null || !GameModel.GAME_TYPE_HACK.equals(request.gameType())) {
            throw new BadRequestException("gameType must be HACK");
        }
        int minutes = request.durationMinutes() == null ? 0 : request.durationMinutes();
        if (minutes < 5 || minutes > 30) throw new BadRequestException("durationMinutes must be between 5 and 30");
        if (request.quizVersionId() == null || request.quizVersionId().isBlank()) {
            throw new BadRequestException("quizVersionId is required");
        }
        requireClass(access, request.classId());
        Classroom classroom = classes.findByTenantIdAndId(access.tenantId(), request.classId())
                .orElseThrow(() -> new ResourceNotFoundException("Class not found"));
        QuizVersion version = versions.findByIdAndTenantId(request.quizVersionId(), access.tenantId())
                .filter(v -> v.getPublishedAt() != null)
                .orElseThrow(() -> new ResourceNotFoundException("Published quiz version not found"));

        Room room = new Room();
        room.id = UUID.randomUUID().toString();
        room.tenantId = access.tenantId();
        room.quizVersionId = version.getId();
        room.quizTitle = quizzes.findByIdAndTenantId(version.getQuizId(), access.tenantId())
                .map(q -> q.getTitle()).orElse("");
        room.classId = classroom.getId();
        room.className = classroom.getName();
        room.hostUserId = access.userId();
        room.durationMinutes = minutes;
        room.rules = rules(request.rules());
        room.createdAt = System.currentTimeMillis();
        room.questions = loadQuestions(access.tenantId(), version);
        if (room.questions.isEmpty()) throw new BadRequestException("The quiz has no playable questions");
        for (Student s : students.findAudienceStudentsInClasses(access.tenantId(), List.of(classroom.getId()),
                PageRequest.of(0, ROSTER_LIMIT))) {
            room.roster.put(s.getAuthUserId(), s.getFullName() == null ? s.getStudentCode() : s.getFullName());
        }
        if (room.roster.isEmpty()) throw new BadRequestException("The class has no students with a login");
        manager.register(room);
        return summary(room);
    }

    /** Batch 1 has no hack, so {@code allowHack} is forced off whatever the caller sent. */
    private static Rules rules(RulesRequest in) {
        RulesRequest r = in == null ? new RulesRequest(null, null, null, null, null, null) : in;
        String order = r.questionOrder() == null ? "RANDOM" : r.questionOrder();
        if (!Set.of("RANDOM", "LESSON", "WRONG_FIRST").contains(order)) {
            throw new BadRequestException("questionOrder must be RANDOM, LESSON or WRONG_FIRST");
        }
        return new Rules(false, Boolean.TRUE.equals(r.emergencyPatch()), !Boolean.FALSE.equals(r.showLeaderboard()),
                !Boolean.FALSE.equals(r.showSolution()), !Boolean.FALSE.equals(r.allowLateJoin()), order);
    }

    /**
     * The player-facing question is the one the quiz player already receives from the published package (feedback
     * blanked, no answer key); answer keys and the feedback texts (used as the solution) come from the version rows.
     */
    @SuppressWarnings("unchecked")
    private List<Question> loadQuestions(String tenantId, QuizVersion version) {
        Map<String, Map<String, Object>> packaged = new LinkedHashMap<>();
        packages.packageEntityForVersion(tenantId, version.getQuizId(), version.getId())
                .map(QuizPackage::getPackageJson)
                .map(json -> json.get("quiz"))
                .filter(Map.class::isInstance)
                .map(quiz -> ((Map<String, Object>) quiz).get("sections"))
                .filter(Collection.class::isInstance)
                .ifPresent(sections -> ((Collection<Object>) sections).forEach(section -> {
                    if (section instanceof Map<?, ?> s && s.get("questions") instanceof Collection<?> list) {
                        list.forEach(q -> {
                            if (q instanceof Map<?, ?> m && m.get("id") != null) {
                                packaged.put(String.valueOf(m.get("id")), (Map<String, Object>) m);
                            }
                        });
                    }
                }));
        List<Question> result = new ArrayList<>();
        for (QuizVersionQuestion row : questionsOf(version.getId())) {
            if (FILTERED_KINDS.contains(row.getKind()) || !"auto".equals(row.getGradingPolicy())
                    || row.getAnswerKey() == null || row.getAnswerKey().isEmpty()) {
                continue;
            }
            Map<String, Object> body = row.getBody() == null ? Map.of() : row.getBody();
            Map<String, Object> payload = new LinkedHashMap<>(
                    packaged.getOrDefault(row.getRuntimeQuestionId(), body));
            payload.putIfAbsent("id", row.getRuntimeQuestionId());
            if (payload.containsKey("feedback")) {
                payload.put("feedback", Map.of("correct", "", "partial", "", "incorrect", ""));
            }
            Question q = new Question();
            q.id = row.getRuntimeQuestionId();
            q.kind = row.getKind();
            q.points = row.getPoints();
            q.title = title(body);
            q.payload = payload;
            q.answerKey = row.getAnswerKey();
            if (body.get("feedback") instanceof Map<?, ?> fb) {
                q.solutionCorrect = fb.get("correct") == null ? "" : String.valueOf(fb.get("correct"));
                q.solutionIncorrect = fb.get("incorrect") == null ? "" : String.valueOf(fb.get("incorrect"));
            }
            result.add(q);
        }
        return result;
    }

    private List<QuizVersionQuestion> questionsOf(String versionId) {
        return questions.findByQuizVersionIdOrderByOrderIndexAsc(versionId);
    }

    private static String title(Map<String, Object> body) {
        for (String key : List.of("title", "prompt", "text", "question", "stem")) {
            if (body.get(key) instanceof String text && !text.isBlank()) {
                return text.length() > 80 ? text.substring(0, 80) : text;
            }
        }
        return "";
    }

    // ---- join / read ----------------------------------------------------------------------------------------

    public JoinResponse join(UserAccessContext access, JoinRequest request) {
        String key = access.tenantId() + ":" + access.userId();
        AtomicInteger failures = joinFailures.get(key, k -> new AtomicInteger());
        if (failures.get() >= MAX_JOIN_FAILURES) {
            throw new TooManyJoinAttemptsException();
        }
        String code = request == null || request.code() == null ? "" : request.code().trim();
        Room room = code.matches("\\d{4}") ? manager.byCode(code) : null;
        if (room == null || !room.tenantId.equals(access.tenantId()) || !room.open()) {
            failures.incrementAndGet();
            throw new ResourceNotFoundException("Room code not found");
        }
        if (!room.roster.containsKey(access.userId())) throw new ForbiddenException("You are not in this room's class");
        if (room.status == Status.ENDING) throw new ConflictException("The game is ending");
        boolean started = room.status == Status.RUNNING || room.status == Status.PAUSED;
        if (started && !room.rules.allowLateJoin() && !room.players.containsKey(access.userId())) {
            throw new ConflictException("The game already started");
        }
        failures.set(0);
        return new JoinResponse(room.id);
    }

    public RoomSummary get(UserAccessContext access, String roomId) {
        Room room = manager.get(roomId);
        if (room == null || !room.tenantId.equals(access.tenantId())) throw new ResourceNotFoundException("Room not found");
        if (!room.hostUserId.equals(access.userId()) && !room.roster.containsKey(access.userId())) {
            throw new ForbiddenException("You are not in this room");
        }
        return summary(room);
    }

    /** Rooms of the caller: live ones from memory, finished ones from Postgres. */
    public List<RoomSummary> list(UserAccessContext access, String statusFilter) {
        Set<String> wanted = statusFilter == null || statusFilter.isBlank() ? Set.of()
                : Set.of(statusFilter.toUpperCase().split(","));
        Map<String, RoomSummary> byId = new LinkedHashMap<>();
        for (Room room : manager.all()) {
            if (room.tenantId.equals(access.tenantId()) && room.hostUserId.equals(access.userId())) {
                byId.put(room.id, summary(room));
            }
        }
        List<com.erg.ergspringboot.lms.internal.game.GameResultWriter.RoomRow> rows =
                writer.history(access.tenantId(), access.userId(), 50);
        Map<String, String> classNames = new LinkedHashMap<>();
        classes.findByTenantIdAndIdIn(access.tenantId(), rows.stream().map(r -> r.classId()).distinct().toList())
                .forEach(c -> classNames.put(c.getId(), c.getName()));
        for (var row : rows) {
            byId.computeIfAbsent(row.id(), id -> new RoomSummary(row.id(), row.gameType(), row.code(), row.status(),
                    row.classId(), classNames.get(row.classId()), row.quizTitle(), row.durationMinutes(),
                    parseRules(row.rulesJson()), row.playerCount(), row.questionCount(), iso(row.createdAt()),
                    iso(row.startedAt()), iso(row.endedAt())));
        }
        return byId.values().stream()
                .filter(s -> wanted.isEmpty() || wanted.contains(s.status()))
                .sorted(Comparator.comparing(RoomSummary::createdAt).reversed())
                .toList();
    }

    private Rules parseRules(String json) {
        try {
            return mapper.readValue(json, Rules.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    RoomSummary summary(Room room) {
        return new RoomSummary(room.id, room.gameType, room.code, room.status.name(), room.classId, room.className,
                room.quizTitle, room.durationMinutes, room.rules, room.players.size(), room.questions.size(),
                iso(room.createdAt), iso(room.startedAt), iso(room.endedAt));
    }

    private static String iso(long epochMs) {
        return epochMs == 0 ? null : Instant.ofEpochMilli(epochMs).toString();
    }

    private static void requireClass(UserAccessContext access, String classId) {
        if (classId == null || classId.isBlank()) throw new BadRequestException("classId is required");
        if (!access.canAccessClass(classId)) throw new ForbiddenException("You do not manage this class");
    }

    /** Too many wrong room codes in a minute. */
    public static final class TooManyJoinAttemptsException extends ApiException {
        public TooManyJoinAttemptsException() {
            super(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", "Too Many Requests",
                    "Too many wrong room codes, try again in a minute");
        }
    }
}

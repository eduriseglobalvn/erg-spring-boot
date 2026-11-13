package com.erg.ergspringboot.elearning.internal.engagement;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.ClassLeaderboardEntryResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.ClassLeaderboardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.LeaderboardClassResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.LeaderboardMeResponse;
import com.erg.ergspringboot.elearning.internal.engagement.LeaderboardXp.Period;
import com.erg.ergspringboot.elearning.internal.engagement.LeaderboardXp.Scope;
import com.erg.ergspringboot.elearning.internal.engagement.LeaderboardXp.Window;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lms.LmsReadCacheInvalidator;
import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import java.text.Collator;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Thi đua" board: students ranked by XP ({@link LeaderboardXp}) for a week, month or term, within their class or
 * their whole grade. Scores are never returned. Students with no XP in the period are listed last without a rank.
 * A student who hid themselves is left out for classmates but still sees their own row. Managers are scoped like
 * discussion moderators and see every student of the class.
 */
@Service
public class ClassLeaderboardService {

    private static final String CLASS_ROWS = "select id, name, leaderboard_enabled from academic_classes where tenant_id = :tenantId";
    private static final int STREAK_LOOKBACK_DAYS = 60;

    private static final String MEMBERS = """
            select s.id, s.full_name, s.auth_user_id, s.leaderboard_hidden from lms_students s
            where s.tenant_id = :tenantId
              and (s.status is null or lower(s.status) in ('active', 'enrolled'))
              and (s.academic_class_id in (:classIds)
                   or exists (select 1 from lms_student_enrollments e
                              where e.tenant_id = s.tenant_id and e.student_id = s.id
                                and e.class_id in (:classIds) and upper(e.status) = 'ENROLLED'))
            """;

    /** First hand-in of each assignment (a retake earns nothing more), from {@code since}. */
    static final String HAND_INS = """
            select student_id, submitted_at, total_questions, before_deadline from (
                select distinct on (a.student_id, a.assignment_id)
                    a.student_id, a.submitted_at, a.total_questions,
                    (not a.late and (asg.due_at is null or a.submitted_at <= asg.due_at)) as before_deadline
                from lms_attempts a
                join lms_assignments asg on asg.tenant_id = a.tenant_id and asg.id = a.assignment_id
                where a.tenant_id = :tenantId and a.student_id in (:studentIds)
                  and a.submitted_at is not null and lower(a.status) in (:states)
                order by a.student_id, a.assignment_id, a.submitted_at, a.id
            ) first_hand_in
            where submitted_at >= :since
            """;

    static final String ACTIVE_DAYS = """
            select distinct a.student_id, cast(a.submitted_at at time zone 'Asia/Ho_Chi_Minh' as date) as day
            from lms_attempts a
            where a.tenant_id = :tenantId and a.student_id in (:studentIds)
              and a.submitted_at >= :since and lower(a.status) in (:states)
            """;

    private final ClassroomRepository classrooms;
    private final StudentRepository students;
    private final LmsReadCacheInvalidator caches;
    private final StudentTierService tiers;
    private final NamedParameterJdbcTemplate jdbc;

    public ClassLeaderboardService(ClassroomRepository classrooms, StudentRepository students,
                                   LmsReadCacheInvalidator caches, JdbcTemplate jdbc, StudentTierService tiers) {
        this.tiers = tiers;
        this.classrooms = classrooms;
        this.students = students;
        this.caches = caches;
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    /** The student's own class ({@code classId}, when given, must be one of theirs); no entries when switched off. */
    @Transactional(readOnly = true)
    public ClassLeaderboardResponse forStudent(String tenantId, String userId, String classId, String period, String scope) {
        Student student = requireStudent(tenantId, userId);
        String id = classId == null || classId.isBlank() ? student.getAcademicClassId() : classId.trim();
        if (id == null || id.isBlank()) throw new ResourceNotFoundException("Authenticated student enrollment was not found.");
        LeaderboardClassResponse target = classRows(tenantId, List.of(id)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Class was not found."));
        // Membership of the requested class itself, so a grade board never opens a classmate class of the same grade.
        Integer self = jdbc.query(MEMBERS + " and s.id = :studentId",
                Map.of("tenantId", tenantId, "classIds", List.of(id), "studentId", student.getId()),
                rs -> rs.next() ? (rs.getBoolean("leaderboard_hidden") ? 1 : 0) : null);
        if (self == null) throw new ForbiddenException("The class is outside the authenticated student's enrollment scope.");
        Board board = board(tenantId, target, period, scope);
        if (!target.leaderboardEnabled()) {
            return board.response(false, self == 1, List.of(), null);
        }
        List<ClassLeaderboardEntryResponse> all = rank(board, userId);
        List<ClassLeaderboardEntryResponse> visible = all.stream()
                .filter(entry -> entry.isViewer() || !board.hidden().contains(entry.studentId())).toList();
        List<ClassLeaderboardEntryResponse> gradeAll = board.scope() == Scope.GRADE ? all
                : rank(board(tenantId, target, board.period(), Scope.GRADE), userId);
        return board.response(true, self == 1, visible, me(all, gradeAll));
    }

    @Transactional
    public boolean setHidden(String tenantId, String userId, boolean hidden) {
        Student student = requireStudent(tenantId, userId);
        jdbc.update("update lms_students set leaderboard_hidden = :hidden, updated_at = now() where tenant_id = :tenantId and id = :id",
                Map.of("hidden", hidden, "tenantId", tenantId, "id", student.getId()));
        return hidden;
    }

    @Transactional(readOnly = true)
    public List<LeaderboardClassResponse> classes(Manager manager) {
        if (manager.tenantWide()) return classRows(manager.tenantId(), null);
        List<String> ids = classrooms.findTeacherClassOptions(manager.tenantId(), manager.userId()).stream()
                .map(option -> option.id()).toList();
        return ids.isEmpty() ? List.of() : classRows(manager.tenantId(), ids);
    }

    @Transactional(readOnly = true)
    public ClassLeaderboardResponse forManager(Manager manager, String classId, String period) {
        LeaderboardClassResponse target = requireClass(manager, classId);
        Board board = board(manager.tenantId(), target, period, null);
        return board.response(target.leaderboardEnabled(), false, rank(board, manager.userId()), null);
    }

    @Transactional
    public LeaderboardClassResponse setEnabled(Manager manager, String classId, boolean enabled) {
        LeaderboardClassResponse target = requireClass(manager, classId);
        jdbc.update("update academic_classes set leaderboard_enabled = :enabled, updated_at = now() where tenant_id = :tenantId and id = :id",
                Map.of("enabled", enabled, "tenantId", manager.tenantId(), "id", target.id()));
        // The (cached) student dashboard tells the student menu whether to show "Thi đua".
        caches.evictAfterCommit(manager.tenantId(), List.of("studentDashboard"));
        return new LeaderboardClassResponse(target.id(), target.name(), enabled);
    }

    private Board board(String tenantId, LeaderboardClassResponse target, String periodValue, String scopeValue) {
        Period period;
        Scope scope;
        try {
            period = LeaderboardXp.period(periodValue);
            scope = LeaderboardXp.scope(scopeValue);
        } catch (IllegalArgumentException invalid) {
            throw new BadRequestException("period must be WEEK, MONTH, TERM or COURSE and scope CLASS or GRADE.");
        }
        return board(tenantId, target, period, scope);
    }

    private Board board(String tenantId, LeaderboardClassResponse target, Period period, Scope scope) {
        List<String> classIds = scope == Scope.GRADE ? gradeClassIds(tenantId, target.id()) : List.of(target.id());
        List<Member> members = jdbc.query(MEMBERS, Map.of("tenantId", tenantId, "classIds", classIds), (rs, row) ->
                new Member(rs.getString("id"), rs.getString("full_name"), rs.getString("auth_user_id"), rs.getBoolean("leaderboard_hidden")));
        Window current = LeaderboardXp.window(period, ZonedDateTime.now(LeaderboardXp.ZONE));
        boolean course = period == Period.COURSE;
        // The course board has no "previous period" column: its rank change is only measured against yesterday.
        Window previous = course ? null : current.previous(period);
        Set<String> hidden = new HashSet<>();
        members.forEach(member -> { if (member.hidden()) hidden.add(member.studentId()); });
        Map<String, Integer> xp = new HashMap<>();
        Map<String, Integer> yesterdayXp = new HashMap<>();
        Map<String, Integer> previousXp = new HashMap<>();
        Map<String, Integer> streaks = new HashMap<>();
        Map<String, Integer> submitted = new HashMap<>();
        if (!members.isEmpty()) {
            List<String> ids = members.stream().map(Member::studentId).toList();
            LocalDate today = LocalDate.now(LeaderboardXp.ZONE);
            ZonedDateTime todayStart = today.atStartOfDay(LeaderboardXp.ZONE);
            LocalDate daysSince = course ? current.start().toLocalDate().minusDays(7) : today.minusDays(STREAK_LOOKBACK_DAYS);
            Map<String, Set<LocalDate>> days = new HashMap<>();
            jdbc.query(ACTIVE_DAYS, Map.of("tenantId", tenantId, "studentIds", ids, "states", LmsStatePolicy.completedAttemptStates(),
                    "since", daysSince.atStartOfDay(LeaderboardXp.ZONE).toOffsetDateTime()), rs -> {
                days.computeIfAbsent(rs.getString("student_id"), key -> new HashSet<>()).add(rs.getObject("day", LocalDate.class));
            });
            days.forEach((studentId, set) -> streaks.put(studentId, LeaderboardXp.streak(set, today)));
            // Course XP is the sum of weekly XP, each week with its own streak bonus (spec 5.2).
            Map<String, Map<LocalDate, Integer>> weekly = new HashMap<>();
            Map<String, Map<LocalDate, Integer>> weeklyYesterday = new HashMap<>();
            jdbc.query(HAND_INS, Map.of("tenantId", tenantId, "studentIds", ids, "states", LmsStatePolicy.completedAttemptStates(),
                    "since", (course ? current.start() : previous.start()).toOffsetDateTime()), rs -> {
                ZonedDateTime at = rs.getObject("submitted_at", java.time.OffsetDateTime.class).atZoneSameInstant(LeaderboardXp.ZONE);
                int earned = LeaderboardXp.handInXp((Integer) rs.getObject("total_questions"), rs.getBoolean("before_deadline"));
                String studentId = rs.getString("student_id");
                if (!at.isBefore(current.start())) {
                    submitted.merge(studentId, 1, Integer::sum);
                    boolean beforeToday = at.isBefore(todayStart);
                    if (course) {
                        LocalDate week = at.toLocalDate().with(java.time.DayOfWeek.MONDAY);
                        weekly.computeIfAbsent(studentId, key -> new HashMap<>()).merge(week, earned, Integer::sum);
                        if (beforeToday) weeklyYesterday.computeIfAbsent(studentId, key -> new HashMap<>()).merge(week, earned, Integer::sum);
                    } else {
                        xp.merge(studentId, earned, Integer::sum);
                        if (beforeToday) yesterdayXp.merge(studentId, earned, Integer::sum);
                    }
                } else if (previous != null && at.isBefore(previous.end())) {
                    previousXp.merge(studentId, earned, Integer::sum);
                }
            });
            if (course) {
                weekly.forEach((studentId, byWeek) -> xp.put(studentId,
                        LeaderboardXp.courseXp(byWeek, days.getOrDefault(studentId, Set.of()), today)));
                weeklyYesterday.forEach((studentId, byWeek) -> yesterdayXp.put(studentId,
                        LeaderboardXp.courseXp(byWeek, days.getOrDefault(studentId, Set.of()), today.minusDays(1))));
            } else {
                xp.replaceAll((studentId, value) -> LeaderboardXp.withStreakBonus(value, streaks.getOrDefault(studentId, 0)));
                yesterdayXp.replaceAll((studentId, value) -> LeaderboardXp.withStreakBonus(value,
                        LeaderboardXp.streak(days.getOrDefault(studentId, Set.of()), today.minusDays(1))));
            }
        }
        return new Board(target, period, scope, current, members, hidden, xp, previousXp, yesterdayXp, streaks, submitted,
                tiers.tiersFor(tenantId, members.stream().map(Member::studentId).toList()));
    }

    /** Same grade, school and year as the class; classes whose teacher switched the board off stay out. */
    private List<String> gradeClassIds(String tenantId, String classId) {
        List<String> ids = jdbc.queryForList("""
                select peer.id from academic_classes peer
                join academic_classes own on own.tenant_id = peer.tenant_id and own.id = :classId
                where peer.tenant_id = :tenantId and peer.school_id = own.school_id and peer.grade = own.grade
                  and peer.academic_year is not distinct from own.academic_year
                  and (peer.leaderboard_enabled or peer.id = own.id)
                """, Map.of("tenantId", tenantId, "classId", classId), String.class);
        return ids.isEmpty() ? List.of(classId) : ids;
    }

    private List<ClassLeaderboardEntryResponse> rank(Board board, String viewerUserId) {
        Map<String, Integer> previousRanks = ranks(board.members(), board.previousXp());
        Map<String, Integer> yesterdayRanks = ranks(board.members(), board.yesterdayXp());
        List<Member> order = ordered(board.members(), board.xp());
        List<ClassLeaderboardEntryResponse> entries = new ArrayList<>(order.size());
        for (int i = 0; i < order.size(); i++) {
            Member member = order.get(i);
            int xp = board.xp().getOrDefault(member.studentId(), 0);
            Integer rank = xp > 0 ? i + 1 : null;
            Integer before = previousRanks.get(member.studentId());
            Integer yesterday = yesterdayRanks.get(member.studentId());
            boolean viewer = viewerUserId != null && viewerUserId.equals(member.authUserId());
            // A student who hid themselves shows no tier or frame to others (spec 5.7).
            Tier tier = member.hidden() && !viewer ? null : board.tiers().get(member.studentId());
            entries.add(new ClassLeaderboardEntryResponse(rank, member.studentId(), member.fullName(), xp,
                    board.streaks().getOrDefault(member.studentId(), 0),
                    rank != null && before != null ? before - rank : null, viewer,
                    rank != null && yesterday != null ? yesterday - rank : null,
                    board.submitted().getOrDefault(member.studentId(), 0),
                    Math.max(0, xp - board.yesterdayXp().getOrDefault(member.studentId(), 0)),
                    tier == null ? null : tier.tierKey(), tier == null ? null : tier.division(),
                    tier == null ? null : tier.frameKey(), member.hidden()));
        }
        return entries;
    }

    private static Map<String, Integer> ranks(List<Member> members, Map<String, Integer> xp) {
        Map<String, Integer> ranks = new HashMap<>();
        List<Member> order = ordered(members, xp);
        for (int i = 0; i < order.size() && xp.getOrDefault(order.get(i).studentId(), 0) > 0; i++) {
            ranks.put(order.get(i).studentId(), i + 1);
        }
        return ranks;
    }

    /** The viewer's standing; {@code grade} is the whole-grade board of the same period (the same board for scope GRADE). */
    static LeaderboardMeResponse me(List<ClassLeaderboardEntryResponse> entries, List<ClassLeaderboardEntryResponse> gradeEntries) {
        ClassLeaderboardEntryResponse mine = entries.stream().filter(ClassLeaderboardEntryResponse::isViewer).findFirst().orElse(null);
        ClassLeaderboardEntryResponse gradeMine = gradeEntries.stream().filter(ClassLeaderboardEntryResponse::isViewer).findFirst().orElse(null);
        if (mine == null) return new LeaderboardMeResponse(null, entries.size(), 0, null, gradeEntries.size(), null, null);
        Integer toNext = null;
        String nextName = null;
        if (mine.rank() != null && mine.rank() > 1) {
            ClassLeaderboardEntryResponse above = entries.get(mine.rank() - 2);
            toNext = above.xp() - mine.xp() + 1; // strictly more XP passes; ties are broken by name
            nextName = "Hạng " + (mine.rank() - 1);
        }
        return new LeaderboardMeResponse(mine.rank(), entries.size(), mine.xp(), gradeMine == null ? null : gradeMine.rank(),
                gradeEntries.size(), toNext, nextName);
    }

    private static List<Member> ordered(List<Member> members, Map<String, Integer> xp) {
        Collator byName = Collator.getInstance(Locale.forLanguageTag("vi"));
        return members.stream().sorted(Comparator.<Member>comparingInt(member -> -xp.getOrDefault(member.studentId(), 0))
                .thenComparing(member -> member.fullName() == null ? "" : member.fullName(), byName)
                .thenComparing(Member::studentId)).toList();
    }

    private Student requireStudent(String tenantId, String userId) {
        return students.findFirstByTenantIdAndAuthUserId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated student was not found."));
    }

    private LeaderboardClassResponse requireClass(Manager manager, String classId) {
        if (classId == null || classId.isBlank()) throw new BadRequestException("classId is required.");
        String id = classId.trim();
        if (!manager.tenantWide() && classrooms.findTeacherClassOptions(manager.tenantId(), manager.userId()).stream()
                .noneMatch(option -> id.equals(option.id()))) {
            throw new ForbiddenException("The class is outside the leaderboard manager scope.");
        }
        return classRows(manager.tenantId(), List.of(id)).stream().findFirst()
                .orElseThrow(() -> new ForbiddenException("The class is outside the leaderboard manager scope."));
    }

    /** {@code ids == null}: every class of the tenant. */
    private List<LeaderboardClassResponse> classRows(String tenantId, List<String> ids) {
        String sql = CLASS_ROWS + (ids == null ? "" : " and id in (:ids)") + " order by name, id";
        Map<String, Object> params = ids == null ? Map.of("tenantId", tenantId) : Map.of("tenantId", tenantId, "ids", ids);
        return jdbc.query(sql, params, (rs, row) ->
                new LeaderboardClassResponse(rs.getString("id"), rs.getString("name"), rs.getBoolean("leaderboard_enabled")));
    }

    public record Manager(String tenantId, String userId, boolean tenantWide) { }

    /** Tier shown next to a student (null fields when untiered or no frame is worn). */
    record Tier(String tierKey, Integer division, String frameKey) { }

    private record Member(String studentId, String fullName, String authUserId, boolean hidden) { }

    private record Board(LeaderboardClassResponse target, Period period, Scope scope, Window window, List<Member> members,
                         Set<String> hidden, Map<String, Integer> xp, Map<String, Integer> previousXp,
                         Map<String, Integer> yesterdayXp, Map<String, Integer> streaks, Map<String, Integer> submitted,
                         Map<String, Tier> tiers) {
        ClassLeaderboardResponse response(boolean enabled, boolean viewerHidden, List<ClassLeaderboardEntryResponse> entries,
                                          LeaderboardMeResponse me) {
            return new ClassLeaderboardResponse(target.id(), target.name(), enabled, period.name(), scope.name(),
                    window.end().toOffsetDateTime().toString(), viewerHidden, entries, me);
        }
    }
}

package com.erg.ergspringboot.elearning.internal.engagement;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.ClassLeaderboardEntryResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.ClassLeaderboardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.LeaderboardClassResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lms.LmsReadCacheInvalidator;
import com.erg.ergspringboot.lms.persistence.ClassLeaderboardRepository;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.lms.persistence.projection.ClassLeaderboardMemberProjection;
import com.erg.ergspringboot.lms.persistence.projection.ClassLeaderboardScoreProjection;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Class leaderboard ("Xếp hạng"): average percent over each student's handed-in assignments of the class (latest
 * handed-in attempt per assignment, as the dashboard average), then more completed assignments, then name. Students
 * with nothing handed in are listed last without a rank. Managers are scoped like discussion moderators.
 */
@Service
public class ClassLeaderboardService {

    private static final String CLASS_ROWS = "select id, name, leaderboard_enabled from academic_classes where tenant_id = :tenantId";

    private final ClassLeaderboardRepository leaderboards;
    private final ClassroomRepository classrooms;
    private final StudentRepository students;
    private final LmsReadCacheInvalidator caches;
    private final NamedParameterJdbcTemplate jdbc;

    public ClassLeaderboardService(ClassLeaderboardRepository leaderboards, ClassroomRepository classrooms,
                                   StudentRepository students, LmsReadCacheInvalidator caches, JdbcTemplate jdbc) {
        this.leaderboards = leaderboards;
        this.classrooms = classrooms;
        this.students = students;
        this.caches = caches;
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    /** The student's own class ({@code classId}, when given, must be one of theirs); no entries when switched off. */
    @Transactional(readOnly = true)
    public ClassLeaderboardResponse forStudent(String tenantId, String userId, String classId) {
        Student student = students.findFirstByTenantIdAndAuthUserId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated student was not found."));
        String id = classId == null || classId.isBlank() ? student.getAcademicClassId() : classId.trim();
        if (id == null || id.isBlank()) throw new ResourceNotFoundException("Authenticated student enrollment was not found.");
        LeaderboardClassResponse target = classRows(tenantId, List.of(id)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Class was not found."));
        List<ClassLeaderboardMemberProjection> members = leaderboards.findClassMembers(tenantId, id);
        if (members.stream().noneMatch(member -> student.getId().equals(member.studentId()))) {
            throw new ForbiddenException("The class is outside the authenticated student's enrollment scope.");
        }
        if (!target.leaderboardEnabled()) return new ClassLeaderboardResponse(id, target.name(), false, List.of());
        return new ClassLeaderboardResponse(id, target.name(), true, rank(members, tenantId, id, userId));
    }

    @Transactional(readOnly = true)
    public List<LeaderboardClassResponse> classes(Manager manager) {
        if (manager.tenantWide()) return classRows(manager.tenantId(), null);
        List<String> ids = classrooms.findTeacherClassOptions(manager.tenantId(), manager.userId()).stream()
                .map(option -> option.id()).toList();
        return ids.isEmpty() ? List.of() : classRows(manager.tenantId(), ids);
    }

    @Transactional(readOnly = true)
    public ClassLeaderboardResponse forManager(Manager manager, String classId) {
        LeaderboardClassResponse target = requireClass(manager, classId);
        List<ClassLeaderboardMemberProjection> members = leaderboards.findClassMembers(manager.tenantId(), target.id());
        return new ClassLeaderboardResponse(target.id(), target.name(), target.leaderboardEnabled(),
                rank(members, manager.tenantId(), target.id(), manager.userId()));
    }

    @Transactional
    public LeaderboardClassResponse setEnabled(Manager manager, String classId, boolean enabled) {
        LeaderboardClassResponse target = requireClass(manager, classId);
        jdbc.update("update academic_classes set leaderboard_enabled = :enabled, updated_at = now() where tenant_id = :tenantId and id = :id",
                Map.of("enabled", enabled, "tenantId", manager.tenantId(), "id", target.id()));
        // The (cached) student dashboard tells the student menu whether to show "Xếp hạng".
        caches.evictAfterCommit(manager.tenantId(), List.of("studentDashboard"));
        return new LeaderboardClassResponse(target.id(), target.name(), enabled);
    }

    private List<ClassLeaderboardEntryResponse> rank(List<ClassLeaderboardMemberProjection> members, String tenantId,
                                                     String classId, String viewerUserId) {
        Map<String, ClassLeaderboardScoreProjection> scores = leaderboards.summarizeClassScores(tenantId, classId).stream()
                .collect(Collectors.toMap(ClassLeaderboardScoreProjection::studentId, Function.identity(), (a, b) -> a));
        Collator byName = Collator.getInstance(Locale.forLanguageTag("vi"));
        Comparator<Row> order = Comparator.<Row>comparingInt(row -> row.completed() > 0 ? 0 : 1)
                .thenComparing(Row::average, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Row::completed, Comparator.reverseOrder())
                .thenComparing(row -> row.member().fullName() == null ? "" : row.member().fullName(), byName);
        List<Row> rows = members.stream().map(member -> {
            ClassLeaderboardScoreProjection score = scores.get(member.studentId());
            long completed = score == null || score.completedCount() == null ? 0 : score.completedCount();
            return new Row(member, completed, completed > 0 ? score.averagePercent() : null);
        }).sorted(order).toList();
        List<ClassLeaderboardEntryResponse> entries = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            entries.add(new ClassLeaderboardEntryResponse(
                    row.completed() > 0 ? i + 1 : null, row.member().studentId(), row.member().fullName(),
                    row.average() == null ? null : Math.round(row.average() * 10) / 10.0, row.completed(),
                    viewerUserId != null && viewerUserId.equals(row.member().authUserId())));
        }
        return entries;
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

    private record Row(ClassLeaderboardMemberProjection member, long completed, Double average) { }
}

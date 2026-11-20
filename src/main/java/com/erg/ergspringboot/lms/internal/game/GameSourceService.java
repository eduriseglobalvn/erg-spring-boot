package com.erg.ergspringboot.lms.internal.game;

import static com.erg.ergspringboot.lms.internal.game.GameRoomService.FILTERED_KINDS;
import static com.erg.ergspringboot.lms.internal.game.GameRoomService.PLAYABLE_SQL;
import static com.erg.ergspringboot.lms.internal.game.GameRoomService.ROSTER_LIMIT;
import static com.erg.ergspringboot.lms.internal.game.GameRoomService.requireClass;

import com.erg.ergspringboot.lms.internal.game.GameDtos.LastGame;
import com.erg.ergspringboot.lms.internal.game.GameDtos.SourceItem;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The quiz picker list for a new room: playable counts, class stats and the last game per quiz. */
@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class GameSourceService {

    private final ClassroomRepository classes;
    private final StudentRepository students;
    private final JdbcTemplate jdbc;

    public GameSourceService(ClassroomRepository classes, StudentRepository students, JdbcTemplate jdbc) {
        this.classes = classes;
        this.students = students;
        this.jdbc = jdbc;
    }

    public List<SourceItem> sources(UserAccessContext access, String classId) {
        // Without classId (exercise bank "last game" column) the class fields are null.
        boolean hasClass = classId != null && !classId.isBlank();
        if (hasClass) requireClass(access, classId);
        Integer classSize = hasClass ? students.findAudienceStudentsInClasses(access.tenantId(), List.of(classId),
                PageRequest.of(0, ROSTER_LIMIT)).size() : null;
        // Questions of each version this class got wrong in earlier ended rooms of the same quiz (one query).
        Map<String, Integer> wrongByVersion = new LinkedHashMap<>();
        if (hasClass) jdbc.query("""
                select v.id as version_id, count(distinct qq.runtime_question_id) as n
                from quiz_versions v
                join quiz_version_questions qq on qq.quiz_version_id = v.id and
                """ + PLAYABLE_SQL + """
                join quiz_versions rv on rv.quiz_id = v.quiz_id and rv.tenant_id = v.tenant_id
                join game_room r on r.quiz_version_id = rv.id and r.tenant_id = v.tenant_id
                     and r.class_id = ? and r.status = 'ENDED'
                join game_answer a on a.room_id = r.id and a.question_id = qq.runtime_question_id
                     and a.context = 'play' and not a.correct
                where v.tenant_id = ? and v.is_active and v.published_at is not null
                group by v.id
                """, rs -> {
            wrongByVersion.put(rs.getString("version_id"), rs.getInt("n"));
        }, classId, access.tenantId());
        // Latest ended room of this teacher per quiz, any version (one query).
        record Last(String roomId, String gameType, String classId, Instant endedAt) { }
        Map<String, Last> lastByQuiz = new LinkedHashMap<>();
        jdbc.query("""
                select distinct on (rv.quiz_id) rv.quiz_id, r.id, r.game_type, r.class_id, r.ended_at
                from game_room r join quiz_versions rv on rv.id = r.quiz_version_id
                where r.tenant_id = ? and r.host_user_id = ? and r.status = 'ENDED' and r.ended_at is not null
                order by rv.quiz_id, r.ended_at desc
                """, rs -> {
            lastByQuiz.put(rs.getString("quiz_id"), new Last(rs.getString("id"), rs.getString("game_type"),
                    rs.getString("class_id"), rs.getTimestamp("ended_at").toInstant()));
        }, access.tenantId(), access.userId());
        Map<String, String> classNames = new LinkedHashMap<>();
        classes.findByTenantIdAndIdIn(access.tenantId(),
                lastByQuiz.values().stream().map(Last::classId).distinct().toList())
                .forEach(c -> classNames.put(c.getId(), c.getName()));
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
            Last last = lastByQuiz.get(first.quizId());
            LastGame lastGame = last == null ? null : new LastGame(last.roomId(), last.gameType(),
                    classNames.get(last.classId()), last.endedAt().toString());
            items.add(new SourceItem(first.versionId(), first.quizId(), first.title(), first.number(),
                    first.publishedAt().toString(), total, total - skipped, skipped, filtered, classSize,
                    hasClass ? wrongByVersion.getOrDefault(first.versionId(), 0) : null, lastGame));
            if (items.size() >= 100) break;
        }
        return items;
    }
}

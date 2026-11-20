package com.erg.ergspringboot.lms.internal.game;

import com.erg.ergspringboot.lms.internal.game.GameModel.AnswerRecord;
import com.erg.ergspringboot.lms.internal.game.GameModel.Player;
import com.erg.ergspringboot.lms.internal.game.GameModel.Room;
import com.erg.ergspringboot.lms.internal.game.GameModel.Status;
import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * The only place a game touches Postgres after room creation: one transaction per finished room (JDBC batch of the
 * room row, its players and every answer). Rooms that end at the same moment queue on a single writer thread.
 */
@Component
public class GameResultWriter {

    private static final Logger LOG = LoggerFactory.getLogger(GameResultWriter.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "game-result-writer");
        thread.setDaemon(true);
        return thread;
    });

    public GameResultWriter(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            @Qualifier("postgresTransactionManager") PlatformTransactionManager transactionManager
    ) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Queues the batch write of a finished room; never blocks the game thread. */
    public void enqueue(Room room) {
        executor.execute(() -> {
            try {
                write(room);
            } catch (RuntimeException failure) {
                LOG.error("Could not persist game room {}", room.id, failure);
            }
        });
    }

    void write(Room room) {
        transaction.executeWithoutResult(status -> {
            jdbc.update("""
                    insert into game_room (id, tenant_id, game_type, join_code, quiz_version_id, class_id, host_user_id,
                        status, duration_minutes, question_count, rules_json, created_at, started_at, ended_at,
                        question_set)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                    on conflict (id) do nothing
                    """,
                    room.id, room.tenantId, room.gameType, room.code, room.quizVersionId, room.classId,
                    room.hostUserId, room.status == Status.CANCELLED ? "CANCELLED" : "ENDED", room.durationMinutes,
                    room.questions.size(), mapper.writeValueAsString(room.rules), ts(room.createdAt),
                    room.startedAt == 0 ? null : ts(room.startedAt), ts(room.endedAt),
                    room.questionSet);
            List<Player> ranked = GameViews.ranking(room);
            List<Object[]> players = new ArrayList<>();
            List<Object[]> answers = new ArrayList<>();
            for (int i = 0; i < ranked.size(); i++) {
                Player p = ranked.get(i);
                players.add(new Object[] {room.id, p.userId, p.displayName, p.stars, p.coins, i + 1, p.correct,
                        p.answered, p.longestStreak});
                for (AnswerRecord a : p.log) {
                    answers.add(new Object[] {room.id, p.userId, a.questionId(), a.attemptNo(), "play", a.correct(),
                            a.answeredMs()});
                }
            }
            jdbc.batchUpdate("""
                    insert into game_player (room_id, user_id, display_name, stars, coins, rank, correct_count,
                        answered_count, longest_streak)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict do nothing
                    """, players);
            jdbc.batchUpdate("""
                    insert into game_answer (room_id, user_id, question_id, attempt_no, context, correct, answered_ms)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """, answers);
        });
    }

    /** Ended rooms of a host, newest first (the live ones come from memory). */
    public List<RoomRow> history(String tenantId, String hostUserId, int limit) {
        return jdbc.query("""
                select r.id, r.game_type, r.join_code, r.status, r.class_id, r.quiz_version_id, r.question_set, v.quiz_id, q.title as quiz_title, r.duration_minutes,
                       r.question_count, r.rules_json::text as rules_json, r.created_at, r.started_at, r.ended_at,
                       (select count(*) from game_player p where p.room_id = r.id) as player_count,
                       (select (count(*) filter (where a.correct))::float8 / nullif(count(*), 0)
                          from game_answer a where a.room_id = r.id and a.context = 'play') as accuracy
                from game_room r
                left join quiz_versions v on v.id = r.quiz_version_id
                left join lms_quizzes q on q.id = v.quiz_id and q.tenant_id = r.tenant_id
                where r.tenant_id = ? and r.host_user_id = ?
                order by r.created_at desc limit ?
                """, (rs, n) -> new RoomRow(rs.getString("id"), rs.getString("game_type"), rs.getString("join_code"),
                        rs.getString("status"), rs.getString("class_id"), rs.getString("quiz_version_id"),
                        rs.getString("quiz_title"), rs.getInt("duration_minutes"), rs.getInt("question_count"), rs.getString("rules_json"),
                        rs.getTimestamp("created_at").getTime(),
                        rs.getTimestamp("started_at") == null ? 0 : rs.getTimestamp("started_at").getTime(),
                        rs.getTimestamp("ended_at") == null ? 0 : rs.getTimestamp("ended_at").getTime(),
                        rs.getInt("player_count"), rs.getString("quiz_id"), rs.getString("question_set"),
                        rs.getObject("accuracy") == null ? null : rs.getDouble("accuracy")), tenantId, hostUserId, limit);
    }

    /** One persisted room as read back for the history list. */
    public record RoomRow(String id, String gameType, String code, String status, String classId, String quizVersionId,
                          String quizTitle, int durationMinutes, int questionCount, String rulesJson, long createdAt, long startedAt,
                          long endedAt, int playerCount, String quizId, String questionSet,
                          Double accuracy) { }

    private static Timestamp ts(long epochMs) {
        return Timestamp.from(Instant.ofEpochMilli(epochMs));
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
    }
}

package com.erg.ergspringboot.lms.internal.game;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.web.socket.WebSocketSession;

/** In-memory state of a game room. Public mutable fields on purpose: Jackson snapshots them to Redis. */
public final class GameModel {

    private GameModel() { }

    public static final String GAME_TYPE_HACK = "HACK";

    public enum Status { LOBBY, COUNTDOWN, RUNNING, PAUSED, ENDING, ENDED, CANCELLED }

    public enum Screen { LOBBY, COUNTDOWN, QUESTION, EXPLAIN, LOOT_PICK, LOOT_RESULT, DONE_WAITING, ENDED }

    public record Rules(boolean allowHack, boolean emergencyPatch, boolean showLeaderboard, boolean showSolution,
                        boolean allowLateJoin, String questionOrder) { }

    public record Loot(String kind, int coins) { }

    public record FeedItem(long at, String text, String tone) { }

    /** One live socket; {@code session} is a concurrent decorator so any thread may send. */
    public record Conn(String userId, boolean host, WebSocketSession session) { }

    /** A serialized message waiting to be written once the room lock is released. */
    public record Out(Conn conn, String json) { }

    public record AnswerRecord(String questionId, int attemptNo, boolean correct, int answeredMs) { }

    /** A playable question: {@code payload} is what clients get; answer key and solutions never leave the server early. */
    public static class Question {
        public String id;
        public String kind;
        public String title;
        public BigDecimal points;
        public Map<String, Object> payload;
        public Map<String, Object> answerKey;
        public String solutionCorrect;
        public String solutionIncorrect;
    }

    public static class Player {
        public String userId;
        public String displayName;
        public int stars;
        public int coins;
        public int streak;
        public int longestStreak;
        public boolean firewall;
        public int correct;
        public int answered;
        public Screen screen = Screen.LOBBY;
        /** Index in {@link Room#questions} of the question on screen, -1 when none. */
        public int current = -1;
        public List<Integer> queue = new ArrayList<>();
        public Set<Integer> everWrong = new LinkedHashSet<>();
        public Map<Integer, Integer> attempts = new LinkedHashMap<>();
        public List<Loot> slots = new ArrayList<>();
        public int pickedSlot = -1;
        public long lastAnswerAt;
        public long questionShownAt;
        public Map<String, Object> lastResult;
        public List<AnswerRecord> log = new ArrayList<>();

        public int total() {
            return stars + coins;
        }
    }

    public static class Room {
        public String id;
        public String tenantId;
        public String gameType = GAME_TYPE_HACK;
        public String code;
        public String quizVersionId;
        public String quizTitle;
        public String classId;
        public String className;
        public String hostUserId;
        public Status status = Status.LOBBY;
        public Rules rules;
        public int durationMinutes;
        public long createdAt;
        public long startedAt;
        public long endedAt;
        public long countdownEndsAt;
        public long endsAt;
        public long pausedRemainingMs;
        public long seq;
        public List<Question> questions = new ArrayList<>();
        /** userId to display name of the class at creation time; only these users may join. */
        public Map<String, String> roster = new LinkedHashMap<>();
        public Map<String, Player> players = new LinkedHashMap<>();
        /** questionId to {answered, wrong} over all players, for the host's class stats. */
        public Map<String, int[]> questionStats = new LinkedHashMap<>();
        public List<Map<String, Object>> finalRows;

        public transient ReentrantLock lock = new ReentrantLock();
        public transient Map<String, Conn> connections = new ConcurrentHashMap<>();
        public transient List<Out> outbox = new ArrayList<>();
        public transient List<FeedItem> feed = new ArrayList<>();
        public transient boolean lobbyDirty;
        public transient boolean boardDirty;
        public transient boolean stateDirty;
        public transient long lastBoardAt;
        public transient long lastStatsAt;
        public transient long lastFeedAt;
        public transient long lastSnapshotAt;
        public transient boolean persisted;

        public boolean open() {
            return status != Status.ENDED && status != Status.CANCELLED;
        }
    }
}

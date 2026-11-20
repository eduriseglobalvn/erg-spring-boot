package com.erg.ergspringboot.lms.internal.game;

import com.erg.ergspringboot.lcms.quiz.publish.GradeResult;
import com.erg.ergspringboot.lms.internal.game.GameModel.AnswerRecord;
import com.erg.ergspringboot.lms.internal.game.GameModel.FeedItem;
import com.erg.ergspringboot.lms.internal.game.GameModel.Loot;
import com.erg.ergspringboot.lms.internal.game.GameModel.Player;
import com.erg.ergspringboot.lms.internal.game.GameModel.Question;
import com.erg.ergspringboot.lms.internal.game.GameModel.Room;
import com.erg.ergspringboot.lms.internal.game.GameModel.Screen;
import com.erg.ergspringboot.lms.internal.game.GameModel.Status;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Pure rules of the HACK game (batch 1). No I/O, no clock, no DB: every method gets {@code now} and mutates the
 * room it is given. The caller serialises access per room (the room lock).
 */
public final class GameEngine {

    /** Grades one raw answer against its key; production passes {@code GradingEngine::grade}. */
    @FunctionalInterface
    public interface Grader {
        GradeResult grade(String kind, Object rawAnswer, Map<String, Object> answerKey, BigDecimal maxPoints);
    }

    /** A rule violation reported to the client as an {@code error} message. */
    public static final class GameException extends RuntimeException {
        private final String code;

        public GameException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private static final int ANSWER_INTERVAL_MS = 1000;
    private static final int MAX_FEED = 40;

    private final GameBalance balance;
    private final Grader grader;
    private final RandomGenerator random;

    public GameEngine(GameBalance balance, Grader grader, RandomGenerator random) {
        this.balance = balance;
        this.grader = grader;
        this.random = random;
    }

    // ---- players -------------------------------------------------------------------------------------------

    /** Adds (or returns) the player of a roster member; late joiners start at once when the game is running. */
    public Player join(Room room, String userId, long now) {
        Player existing = room.players.get(userId);
        if (existing != null) return existing;
        String name = room.roster.get(userId);
        if (name == null) throw new GameException("NOT_IN_CLASS", "Not a member of this room's class");
        boolean late = room.status == Status.RUNNING || room.status == Status.PAUSED;
        if (late && !room.rules.allowLateJoin()) throw new GameException("LATE_JOIN_CLOSED", "The game already started");
        if (!room.open() || room.status == Status.ENDING) throw new GameException("ROOM_CLOSED", "The room is closed");
        Player player = new Player();
        player.userId = userId;
        player.displayName = name;
        room.players.put(userId, player);
        room.lobbyDirty = true;
        room.stateDirty = true;
        if (room.status == Status.COUNTDOWN) player.screen = Screen.COUNTDOWN;
        if (late) beginPlayer(room, player, now);
        return player;
    }

    // ---- room lifecycle ------------------------------------------------------------------------------------

    public void startCountdown(Room room, long now) {
        if (room.status != Status.LOBBY) throw new GameException("BAD_STATE", "The game cannot start now");
        if (room.questions.isEmpty()) throw new GameException("NO_QUESTIONS", "The room has no playable questions");
        room.status = Status.COUNTDOWN;
        room.countdownEndsAt = now + balance.getCountdownMs();
        room.players.values().forEach(p -> p.screen = Screen.COUNTDOWN);
        room.stateDirty = true;
    }

    /** Advances time-driven phases. Returns true when the status changed (callers broadcast {@code room.phase}). */
    public boolean tick(Room room, long now) {
        if (room.status == Status.COUNTDOWN && now >= room.countdownEndsAt) {
            room.status = Status.RUNNING;
            room.startedAt = room.countdownEndsAt;
            room.endsAt = room.countdownEndsAt + room.durationMinutes * 60_000L;
            room.players.values().forEach(p -> beginPlayer(room, p, now));
            room.stateDirty = true;
            return true;
        }
        if (room.status == Status.RUNNING && now >= room.endsAt) {
            room.status = Status.ENDING;
            room.stateDirty = true;
            return true;
        }
        return false;
    }

    public void pause(Room room, long now) {
        if (room.status != Status.RUNNING) throw new GameException("BAD_STATE", "The game is not running");
        room.pausedRemainingMs = Math.max(0, room.endsAt - now);
        room.status = Status.PAUSED;
        room.stateDirty = true;
    }

    public void resume(Room room, long now) {
        if (room.status != Status.PAUSED) throw new GameException("BAD_STATE", "The game is not paused");
        room.endsAt = now + room.pausedRemainingMs;
        room.pausedRemainingMs = 0;
        room.status = Status.RUNNING;
        room.stateDirty = true;
    }

    /** Host ends the game early: ENDING now, {@link #finish} computes the result. */
    public void requestEnd(Room room, long now) {
        if (!room.open() || room.status == Status.ENDING) return;
        if (room.status == Status.LOBBY || room.status == Status.COUNTDOWN) {
            room.status = Status.CANCELLED;
            room.endedAt = now;
            room.finalRows = new ArrayList<>();
            room.players.values().forEach(p -> p.screen = Screen.ENDED);
            room.stateDirty = true;
            return;
        }
        room.status = Status.ENDING;
        room.stateDirty = true;
    }

    /** ENDING to ENDED: answers not submitted are simply not counted; freezes ranks into {@link Room#finalRows}. */
    public void finish(Room room, long now) {
        if (room.status != Status.ENDING) return;
        room.status = Status.ENDED;
        room.endedAt = now;
        room.endsAt = Math.min(room.endsAt, now);
        room.pausedRemainingMs = 0;
        List<Player> ranked = GameViews.ranking(room);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) rows.add(GameViews.finalRow(ranked.get(i), i + 1));
        room.finalRows = rows;
        room.players.values().forEach(p -> p.screen = Screen.ENDED);
        room.stateDirty = true;
    }

    // ---- play ----------------------------------------------------------------------------------------------

    private void beginPlayer(Room room, Player player, long now) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < room.questions.size(); i++) order.add(i);
        if ("RANDOM".equals(room.rules.questionOrder())) Collections.shuffle(order, random);
        player.queue = order;
        showNext(room, player, now);
    }

    private void showNext(Room room, Player player, long now) {
        if (player.queue.isEmpty()) player.queue.addAll(player.everWrong);
        if (player.queue.isEmpty()) {
            player.current = -1;
            player.screen = Screen.DONE_WAITING;
            return;
        }
        player.current = player.queue.remove(0);
        player.screen = Screen.QUESTION;
        player.questionShownAt = now;
        player.slots = new ArrayList<>();
        player.pickedSlot = -1;
    }

    /** Grades an answer and returns the {@code answer.result} payload. */
    public Map<String, Object> answer(Room room, Player player, String questionId, Object raw, long now) {
        requireRunning(room, now);
        if (player.screen != Screen.QUESTION || player.current < 0
                || !room.questions.get(player.current).id.equals(questionId)) {
            throw new GameException("NOT_CURRENT_QUESTION", "This is not your current question");
        }
        if (now - player.lastAnswerAt < ANSWER_INTERVAL_MS) throw new GameException("RATE_LIMITED", "Too many answers");
        player.lastAnswerAt = now;
        Question q = room.questions.get(player.current);
        GradeResult graded = grader.grade(q.kind, raw, q.answerKey, q.points);
        boolean correct = Boolean.TRUE.equals(graded.correct());
        int attempt = player.attempts.merge(player.current, 1, Integer::sum);
        player.answered++;
        player.log.add(new AnswerRecord(q.id, attempt, correct, (int) Math.min(Integer.MAX_VALUE, now - player.questionShownAt)));
        int[] stats = room.questionStats.computeIfAbsent(q.id, k -> new int[2]);
        stats[0]++;
        int stars = 0;
        Integer requeue = null;
        if (correct) {
            player.streak++;
            player.longestStreak = Math.max(player.longestStreak, player.streak);
            stars = balance.getBaseStars() + balance.getStreakStars() * Math.min(player.streak, balance.getStreakCap());
            player.stars += stars;
            player.correct++;
            boolean firewallOn = player.streak >= balance.getFirewallStreak();
            if (firewallOn && !player.firewall) feed(room, now, player.displayName + " bật tường lửa", "firewall");
            player.firewall = firewallOn;
            player.slots = rollSlots(room.rules.allowHack());
            player.pickedSlot = -1;
            player.screen = Screen.LOOT_PICK;
        } else {
            stats[1]++;
            player.streak = 0;
            player.firewall = false;
            player.everWrong.add(player.current);
            player.queue.add(Math.min(balance.getRequeueAfter(), player.queue.size()), player.current);
            requeue = balance.getRequeueAfter();
            player.screen = Screen.EXPLAIN;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionId", q.id);
        result.put("correct", correct);
        result.put("correctAnswer", q.answerKey);
        result.put("solution", room.rules.showSolution() ? solution(q, correct) : "");
        result.put("starsGained", stars);
        result.put("me", GameViews.playerState(room, player));
        if (requeue != null) result.put("requeueAfter", requeue);
        player.lastResult = result;
        room.boardDirty = true;
        room.stateDirty = true;
        return result;
    }

    /** Opens one of the 3 packets and returns the {@code loot.result} payload (all 3 revealed). */
    public Map<String, Object> pick(Room room, Player player, int slot, long now) {
        requireRunning(room, now);
        if (player.screen != Screen.LOOT_PICK) throw new GameException("NOT_LOOT_PICK", "No packets to open");
        if (slot < 0 || slot >= player.slots.size()) throw new GameException("BAD_SLOT", "Slot must be 0-2");
        Loot picked = player.slots.get(slot);
        player.pickedSlot = slot;
        player.coins += picked.coins();
        player.screen = Screen.LOOT_RESULT;
        if ("VAULT".equals(picked.kind()) || "BIG_WALLET".equals(picked.kind())) {
            feed(room, now, player.displayName + " mở được +" + picked.coins() + " xu", "loot");
        }
        List<Map<String, Object>> slots = new ArrayList<>();
        for (Loot loot : player.slots) slots.add(Map.of("kind", loot.kind(), "coins", loot.coins()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pickedSlot", slot);
        result.put("slots", slots);
        result.put("me", GameViews.playerState(room, player));
        room.boardDirty = true;
        room.stateDirty = true;
        return result;
    }

    /** Leaves the explanation or loot-result screen. Returns true when a new screen is shown. */
    public boolean next(Room room, Player player, long now) {
        if (room.status != Status.RUNNING) return false;
        if (player.screen != Screen.EXPLAIN && player.screen != Screen.LOOT_RESULT && player.screen != Screen.DONE_WAITING) {
            return false;
        }
        showNext(room, player, now);
        room.stateDirty = true;
        return true;
    }

    List<Loot> rollSlots(boolean allowHack) {
        List<Loot> slots = new ArrayList<>(List.of(gift(allowHack), gift(allowHack)));
        slots.add(random.nextInt(3), new Loot("EMPTY", 0));
        return slots;
    }

    private Loot gift(boolean allowHack) {
        int total = 0;
        for (String kind : GameBalance.GIFT_ORDER) total += balance.weight(kind, allowHack);
        int roll = random.nextInt(total);
        for (String kind : GameBalance.GIFT_ORDER) {
            roll -= balance.weight(kind, allowHack);
            if (roll < 0) return new Loot(kind, balance.coinsOf(kind));
        }
        return new Loot("SCRAP", balance.coinsOf("SCRAP"));
    }

    private void requireRunning(Room room, long now) {
        if (room.status == Status.PAUSED) throw new GameException("ROOM_PAUSED", "The game is paused");
        if (room.status != Status.RUNNING || now >= room.endsAt) throw new GameException("ROOM_NOT_RUNNING", "The game is not running");
    }

    private static String solution(Question q, boolean correct) {
        String first = correct ? q.solutionCorrect : q.solutionIncorrect;
        String second = correct ? q.solutionIncorrect : q.solutionCorrect;
        return first != null && !first.isBlank() ? first : second == null ? "" : second;
    }

    private void feed(Room room, long now, String text, String tone) {
        room.feed.add(new FeedItem(now, text, tone));
        if (room.feed.size() > MAX_FEED) room.feed.remove(0);
    }
}

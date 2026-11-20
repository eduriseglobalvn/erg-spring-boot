package com.erg.ergspringboot.lms.internal.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.lcms.quiz.publish.GradeResult;
import com.erg.ergspringboot.lms.internal.game.GameEngine.GameException;
import com.erg.ergspringboot.lms.internal.game.GameModel.Loot;
import com.erg.ergspringboot.lms.internal.game.GameModel.Player;
import com.erg.ergspringboot.lms.internal.game.GameModel.Question;
import com.erg.ergspringboot.lms.internal.game.GameModel.Room;
import com.erg.ergspringboot.lms.internal.game.GameModel.Rules;
import com.erg.ergspringboot.lms.internal.game.GameModel.Screen;
import com.erg.ergspringboot.lms.internal.game.GameModel.Status;
import com.erg.ergspringboot.lms.internal.quiz.runtime.GradingEngine;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GameEngineTest {

    private static final long T0 = 1_000_000L;

    private final GameBalance balance = new GameBalance();
    /** "ok" is right, "part" is partly right (multi-part answer), anything else is wrong. */
    private final GameEngine engine = new GameEngine(balance, (kind, raw, key, max) -> switch (String.valueOf(raw)) {
        case "ok" -> new GradeResult(true, false, max, false);
        case "part" -> new GradeResult(false, true, max.divide(BigDecimal.TWO), false);
        default -> new GradeResult(false, false, BigDecimal.ZERO, false);
    }, new Random(42));

    private Room room(int questionCount, String order) {
        Room room = new Room();
        room.id = "r1";
        room.code = "1234";
        room.durationMinutes = 10;
        room.rules = new Rules(false, false, true, true, true, order);
        for (int i = 0; i < questionCount; i++) {
            Question q = new Question();
            q.id = "q" + i;
            q.kind = "single_choice";
            q.title = "Question " + i;
            q.points = BigDecimal.ONE;
            q.answerKey = Map.of("correctChoiceId", "a");
            q.payload = Map.of("id", q.id);
            room.questions.add(q);
        }
        room.roster.put("u1", "An");
        room.roster.put("u2", "Binh");
        return room;
    }

    /** Joins u1, starts the game and moves the clock to the first question. */
    private Player started(Room room) {
        Player p = engine.join(room, "u1", T0);
        engine.startCountdown(room, T0);
        engine.tick(room, T0 + balance.getCountdownMs());
        assertThat(room.status).isEqualTo(Status.RUNNING);
        return p;
    }

    private long now = T0 + 10_000;

    private Map<String, Object> answer(Room room, Player p, String raw) {
        now += 1100;
        return engine.answer(room, p, room.questions.get(p.current).id, raw, now);
    }

    private void passLoot(Room room, Player p) {
        now += 10;
        engine.pick(room, p, 0, now);
        engine.next(room, p, now);
    }

    @Test
    void starsGrowWithStreakAndCapAtFive() {
        Room room = room(10, "LESSON");
        Player p = started(room);
        int[] expected = {110, 120, 130, 140, 150, 150};
        int total = 0;
        for (int gain : expected) {
            assertThat(answer(room, p, "ok")).containsEntry("starsGained", gain);
            total += gain;
            passLoot(room, p);
        }
        assertThat(p.stars).isEqualTo(total);
        assertThat(p.streak).isEqualTo(6);
    }

    @Test
    void firewallTurnsOnAtStreakThreeAndOffOnWrong() {
        Room room = room(10, "LESSON");
        Player p = started(room);
        answer(room, p, "ok");
        passLoot(room, p);
        answer(room, p, "ok");
        passLoot(room, p);
        assertThat(p.firewall).isFalse();
        answer(room, p, "ok");
        assertThat(p.firewall).isTrue();
        passLoot(room, p);
        int starsBefore = p.stars;
        Map<String, Object> wrong = answer(room, p, "no");
        assertThat(p.firewall).isFalse();
        assertThat(p.streak).isZero();
        assertThat(p.stars).as("a wrong answer never takes stars").isEqualTo(starsBefore);
        assertThat(wrong).containsEntry("correct", false).containsEntry("requeueAfter", 3);
    }

    @Test
    void multiPartAnswerCountsOnlyWhenEverythingIsRight() {
        Room room = room(5, "LESSON");
        Player p = started(room);
        assertThat(answer(room, p, "part")).containsEntry("correct", false).containsEntry("starsGained", 0);
        assertThat(p.stars).isZero();
    }

    @Test
    void wrongQuestionComesBackAfterThreeOthers() {
        Room room = room(10, "LESSON");
        Player p = started(room);
        assertThat(p.current).isZero();
        answer(room, p, "no");
        assertThat(p.screen).isEqualTo(Screen.EXPLAIN);
        engine.next(room, p, now);
        assertThat(p.current).isEqualTo(1);
        answer(room, p, "ok");
        passLoot(room, p);
        assertThat(p.current).isEqualTo(2);
        answer(room, p, "ok");
        passLoot(room, p);
        assertThat(p.current).isEqualTo(3);
        answer(room, p, "ok");
        passLoot(room, p);
        assertThat(p.current).as("question 0 returns after three others").isZero();
    }

    @Test
    void runningOutOfQuestionsReplaysTheOnesGotWrong() {
        Room room = room(2, "LESSON");
        Player p = started(room);
        answer(room, p, "no");
        engine.next(room, p, now);
        answer(room, p, "ok");
        passLoot(room, p);
        assertThat(p.current).isZero();
        answer(room, p, "ok");
        passLoot(room, p);
        assertThat(p.current).as("queue empty: replay the ever-wrong question").isZero();
        assertThat(p.screen).isEqualTo(Screen.QUESTION);
    }

    @Test
    void everythingRightAndNothingToReplayWaitsForTheClock() {
        Room room = room(1, "LESSON");
        Player p = started(room);
        answer(room, p, "ok");
        passLoot(room, p);
        assertThat(p.screen).isEqualTo(Screen.DONE_WAITING);
    }

    @Test
    void threePacketsAreAlwaysTwoGiftsAndOneEmpty() {
        for (int i = 0; i < 2000; i++) {
            var slots = engine.rollSlots(i % 2 == 0);
            assertThat(slots).hasSize(3);
            assertThat(slots.stream().filter(l -> l.kind().equals("EMPTY"))).hasSize(1);
            assertThat(slots.stream().filter(l -> !l.kind().equals("EMPTY") && l.coins() >= 0)).hasSize(2);
        }
    }

    @Test
    void emptyPacketMovesAroundAllThreePositions() {
        int[] seen = new int[3];
        for (int i = 0; i < 600; i++) {
            var slots = engine.rollSlots(false);
            for (int s = 0; s < 3; s++) if (slots.get(s).kind().equals("EMPTY")) seen[s]++;
        }
        for (int n : seen) assertThat(n).isGreaterThan(120);
    }

    @Test
    void giftDistributionFollowsTheTableWithHackOff() {
        Map<String, Integer> counts = new HashMap<>();
        int gifts = 0;
        for (int i = 0; i < 40_000; i++) {
            for (Loot l : engine.rollSlots(false)) {
                if (l.kind().equals("EMPTY")) continue;
                counts.merge(l.kind(), 1, Integer::sum);
                gifts++;
            }
        }
        assertThat(counts).doesNotContainKey("HACK_COMMAND");
        assertThat(counts.get("SCRAP") / (double) gifts).isBetween(0.63, 0.67);
        assertThat(counts.get("SMALL_WALLET") / (double) gifts).isBetween(0.20, 0.24);
        assertThat(counts.get("BIG_WALLET") / (double) gifts).isBetween(0.08, 0.12);
        assertThat(counts.get("VAULT") / (double) gifts).isBetween(0.02, 0.04);
    }

    @Test
    void giftDistributionKeepsHackCommandsWhenHackIsOn() {
        Map<String, Integer> counts = new HashMap<>();
        int gifts = 0;
        for (int i = 0; i < 40_000; i++) {
            for (Loot l : engine.rollSlots(true)) {
                if (l.kind().equals("EMPTY")) continue;
                counts.merge(l.kind(), 1, Integer::sum);
                gifts++;
            }
        }
        assertThat(counts.get("SCRAP") / (double) gifts).isBetween(0.58, 0.62);
        assertThat(counts.get("HACK_COMMAND") / (double) gifts).isBetween(0.04, 0.06);
    }

    @Test
    void pickedPacketPaysCoinsAndRevealsAllThree() {
        Room room = room(5, "LESSON");
        Player p = started(room);
        answer(room, p, "ok");
        int coinsOfSlot1 = p.slots.get(1).coins();
        Map<String, Object> result = engine.pick(room, p, 1, now);
        assertThat(p.coins).isEqualTo(coinsOfSlot1);
        assertThat((java.util.List<?>) result.get("slots")).hasSize(3);
        assertThat(result).containsEntry("pickedSlot", 1);
    }

    @Test
    void answersAreRateLimitedToOnePerSecond() {
        Room room = room(5, "LESSON");
        Player p = started(room);
        now += 1100;
        engine.answer(room, p, "q0", "no", now);
        engine.next(room, p, now);
        assertThatThrownBy(() -> engine.answer(room, p, room.questions.get(p.current).id, "ok", now + 200))
                .isInstanceOf(GameException.class).hasMessageContaining("Too many");
    }

    @Test
    void timeUpEndsTheGameAndLateAnswersDoNotCount() {
        Room room = room(5, "LESSON");
        Player p = started(room);
        answer(room, p, "ok");
        long endsAt = room.endsAt;
        assertThat(endsAt).isEqualTo(T0 + balance.getCountdownMs() + 10 * 60_000L);
        assertThat(engine.tick(room, endsAt - 1)).isFalse();
        assertThat(engine.tick(room, endsAt)).isTrue();
        assertThat(room.status).isEqualTo(Status.ENDING);
        engine.finish(room, endsAt);
        assertThat(room.status).isEqualTo(Status.ENDED);
        assertThat(room.finalRows).hasSize(1);
        assertThatThrownBy(() -> engine.pick(room, p, 0, endsAt + 1)).isInstanceOf(GameException.class);
        assertThat(p.answered).isEqualTo(1);
    }

    @Test
    void pauseFreezesTheClockAndResumeRestoresIt() {
        Room room = room(5, "LESSON");
        Player p = started(room);
        long pausedAt = room.startedAt + 60_000;
        engine.pause(room, pausedAt);
        assertThat(room.pausedRemainingMs).isEqualTo(9 * 60_000L);
        assertThat(engine.tick(room, pausedAt + 3_600_000L)).isFalse();
        assertThatThrownBy(() -> engine.answer(room, p, "q0", "ok", pausedAt + 5000))
                .isInstanceOf(GameException.class).extracting("code").isEqualTo("ROOM_PAUSED");
        long resumedAt = pausedAt + 120_000;
        engine.resume(room, resumedAt);
        assertThat(room.endsAt).isEqualTo(resumedAt + 9 * 60_000L);
    }

    @Test
    void rankingIsStarsPlusCoins() {
        Room room = room(5, "LESSON");
        Player a = started(room);
        Player b = engine.join(room, "u2", T0);
        a.stars = 100;
        b.stars = 100;
        b.coins = 50;
        assertThat(engine.rank(room, b)).isEqualTo(1);
        assertThat(engine.rank(room, a)).isEqualTo(2);
    }

    @Test
    void nonMembersCannotJoinAndGradingEngineNeedsNoDatabase() {
        Room room = room(2, "LESSON");
        assertThatThrownBy(() -> engine.join(room, "stranger", T0)).isInstanceOf(GameException.class);
        // GradingEngine is a plain object over static strategies: it runs with no Spring context or DB.
        GradeResult graded = new GradingEngine().grade("single_choice", Map.of("choiceId", "a"),
                Map.of("correctChoiceId", "a"), BigDecimal.ONE);
        assertThat(graded.correct()).isTrue();
    }
}

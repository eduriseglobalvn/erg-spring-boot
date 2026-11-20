package com.erg.ergspringboot.lms.internal.game;

import com.erg.ergspringboot.lms.internal.game.GameModel.Conn;
import com.erg.ergspringboot.lms.internal.game.GameModel.Player;
import com.erg.ergspringboot.lms.internal.game.GameModel.Question;
import com.erg.ergspringboot.lms.internal.game.GameModel.Room;
import com.erg.ergspringboot.lms.internal.game.GameModel.Screen;
import com.erg.ergspringboot.lms.internal.game.GameModel.Status;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only views of a room: ranking and the JSON-shaped payloads sent to clients. No state changes here. */
final class GameViews {

    private GameViews() { }

    static final int HOST_TOP = 8;
    static final int STUDENT_TOP = 5;

    private static final Comparator<Player> BY_SCORE = Comparator
            .comparingInt(Player::total).reversed()
            .thenComparing(Comparator.comparingInt((Player p) -> p.stars).reversed())
            .thenComparing(p -> p.displayName);

    static List<Player> ranking(Room room) {
        List<Player> list = new ArrayList<>(room.players.values());
        list.sort(BY_SCORE);
        return list;
    }

    static int rank(Room room, Player player) {
        int better = 0;
        for (Player other : room.players.values()) {
            if (other != player && BY_SCORE.compare(other, player) < 0) better++;
        }
        return better + 1;
    }

    static Map<String, Object> playerState(Room room, Player p) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("playerId", p.userId);
        state.put("displayName", p.displayName);
        state.put("stars", p.stars);
        state.put("coins", p.coins);
        state.put("streak", p.streak);
        state.put("firewall", p.firewall);
        state.put("rank", rank(room, p));
        state.put("playerCount", room.players.size());
        state.put("answeredCount", p.answered);
        state.put("correctCount", p.correct);
        return state;
    }

    static List<Map<String, Object>> top(Room room, int n) {
        List<Player> ranked = ranking(room);
        List<Map<String, Object>> top = new ArrayList<>();
        for (int i = 0; i < Math.min(n, ranked.size()); i++) {
            Player p = ranked.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("playerId", p.userId);
            row.put("displayName", p.displayName);
            row.put("stars", p.stars);
            row.put("coins", p.coins);
            row.put("total", p.total());
            row.put("rank", i + 1);
            row.put("firewall", p.firewall);
            top.add(row);
        }
        return top;
    }

    static Map<String, Object> finalRow(Player p, int rank) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("playerId", p.userId);
        row.put("displayName", p.displayName);
        row.put("stars", p.stars);
        row.put("coins", p.coins);
        row.put("total", p.total());
        row.put("rank", rank);
        row.put("correctCount", p.correct);
        row.put("answeredCount", p.answered);
        row.put("longestStreak", p.longestStreak);
        return row;
    }

    /** {@code me} of {@code room.ended}: the final row plus the questions this player got wrong at least once. */
    static Map<String, Object> finalMe(Room room, Player p) {
        Map<String, Object> me = finalRow(p, rank(room, p));
        List<Map<String, Object>> weak = new ArrayList<>();
        for (int index : p.everWrong) {
            Question q = room.questions.get(index);
            long wrongCount = p.log.stream().filter(r -> r.questionId().equals(q.id) && !r.correct()).count();
            weak.add(Map.of("questionId", q.id, "title", q.title == null ? "" : q.title,
                    "index", index, "wrongCount", (int) wrongCount));
        }
        me.put("weakQuestions", weak);
        return me;
    }

    static Map<String, Object> classStats(Room room, int playersOnline) {
        int answered = 0;
        int wrong = 0;
        List<Map<String, Object>> per = new ArrayList<>();
        for (Question q : room.questions) {
            int[] s = room.questionStats.getOrDefault(q.id, new int[2]);
            answered += s[0];
            wrong += s[1];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("questionId", q.id);
            row.put("title", q.title == null ? "" : q.title);
            row.put("wrongRate", s[0] == 0 ? 0.0 : (double) s[1] / s[0]);
            int index = room.questions.indexOf(q);
            int wrongPlayers = 0;
            int answeredPlayers = 0;
            for (Player p : room.players.values()) {
                if (p.everWrong.contains(index)) wrongPlayers++;
                if (p.attempts.containsKey(index)) answeredPlayers++;
            }
            row.put("wrongPlayers", wrongPlayers);
            row.put("answeredPlayers", answeredPlayers);
            per.add(row);
        }
        per.sort(Comparator.comparingInt((Map<String, Object> r) -> (int) r.get("wrongPlayers")).reversed());
        int firewallOn = 0;
        int progress = 0;
        for (Player p : room.players.values()) {
            if (p.firewall) firewallOn++;
            progress += p.correct;
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("answeredTotal", answered);
        stats.put("accuracy", answered == 0 ? 0.0 : (double) (answered - wrong) / answered);
        stats.put("perQuestion", per);
        stats.put("playersOnline", playersOnline);
        stats.put("playerCount", room.players.size());
        stats.put("avgProgress", room.players.isEmpty() ? 0.0 : Math.round(progress * 10.0 / room.players.size()) / 10.0);
        stats.put("questionTotal", room.questions.size());
        stats.put("firewallOn", firewallOn);
        stats.put("hackWins", 0);
        return stats;
    }
    static Map<String, Object> phase(Room room) {
        Map<String, Object> phase = new LinkedHashMap<>();
        phase.put("phase", room.status.name());
        phase.put("endsAt", room.endsAt == 0 ? null : room.endsAt);
        phase.put("pausedRemainingMs", room.pausedRemainingMs);
        return phase;
    }

    /** {@code missing} (roster minus joined players) is for the host only; students get an empty list. */
    static Map<String, Object> lobby(Room room, boolean host) {
        List<Map<String, Object>> players = new ArrayList<>();
        Set<String> joined = new HashSet<>();
        for (Conn c : room.connections.values()) {
            Player p = room.players.get(c.userId());
            if (p != null) {
                players.add(Map.of("playerId", p.userId, "displayName", p.displayName));
                joined.add(p.userId);
            }
        }
        List<Map<String, Object>> missing = new ArrayList<>();
        if (host) {
            room.roster.forEach((id, name) -> {
                if (!joined.contains(id)) missing.add(Map.of("userId", id, "displayName", name == null ? "" : name));
            });
        }
        return Map.of("players", players, "count", players.size(), "classSize", room.roster.size(),
                "missing", missing);
    }

    static Map<String, Object> questionMessage(Room room, Player p) {
        Map<String, Object> message = new LinkedHashMap<>();
        int total = room.questions.size();
        message.put("index", Math.min(p.correct + 1, total));
        message.put("total", total);
        message.put("question", room.questions.get(p.current).payload);
        return message;
    }

    static Map<String, Object> ended(Room room, Conn conn) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("final", room.finalRows);
        Player p = room.players.get(conn.userId());
        if (p != null) data.put("me", finalMe(room, p));
        return data;
    }

    /** Full snapshot for a socket: what {@code resume} and a fresh connection receive. */
    static Map<String, Object> state(Room room, Conn conn, long now) {
        Map<String, Object> state = new LinkedHashMap<>();
        Player p = conn.host() ? null : room.players.get(conn.userId());
        state.put("phase", room.status.name());
        state.put("serverNow", now);
        state.put("endsAt", room.endsAt == 0 ? null : room.endsAt);
        state.put("pausedRemainingMs", room.pausedRemainingMs);
        state.put("me", p == null ? null : playerState(room, p));
        Screen screen = p != null ? p.screen : switch (room.status) {
            case LOBBY -> Screen.LOBBY;
            case COUNTDOWN -> Screen.COUNTDOWN;
            case ENDED, CANCELLED -> Screen.ENDED;
            default -> Screen.QUESTION;
        };
        state.put("screen", screen.name());
        if (p != null && screen == Screen.QUESTION && p.current >= 0) state.put("question", questionMessage(room, p));
        if (p != null && (screen == Screen.EXPLAIN || screen == Screen.LOOT_PICK || screen == Screen.LOOT_RESULT)) {
            state.put("lastResult", p.lastResult);
        }
        // Not in the contract table: lets a reconnect rebuild the loot screens.
        if (p != null && screen == Screen.LOOT_PICK) state.put("loot", Map.of("slots", 3));
        if (p != null && screen == Screen.LOOT_RESULT) {
            List<Map<String, Object>> slots = new ArrayList<>();
            p.slots.forEach(l -> slots.add(Map.of("kind", l.kind(), "coins", l.coins())));
            state.put("loot", Map.of("pickedSlot", p.pickedSlot, "slots", slots));
        }
        if (room.rules.showLeaderboard() || conn.host()) {
            state.put("leaderboard", Map.of("top", top(room, conn.host() ? HOST_TOP : STUDENT_TOP)));
        }
        if (room.status == Status.LOBBY || room.status == Status.COUNTDOWN) state.put("lobby", lobby(room, conn.host()));
        if (room.status == Status.COUNTDOWN) state.put("countdownStartsAt", room.countdownEndsAt);
        if (room.status == Status.ENDED || room.status == Status.CANCELLED) state.put("ended", ended(room, conn));
        return state;
    }
}

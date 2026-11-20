package com.erg.ergspringboot.lms.internal.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.erg.ergspringboot.lms.internal.game.GameModel.Conn;
import com.erg.ergspringboot.lms.internal.game.GameModel.Question;
import com.erg.ergspringboot.lms.internal.game.GameModel.Room;
import com.erg.ergspringboot.lms.internal.game.GameModel.Rules;
import com.erg.ergspringboot.lms.internal.game.GameModel.Status;
import com.erg.ergspringboot.lms.internal.quiz.runtime.GradingEngine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The socket flow without a server: lobby, start, answer, throttled broadcasts, end, kicking a duplicate login. */
class GameRoomManagerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final GameResultWriter writer = mock(GameResultWriter.class);
    private final GameRoomManager manager = new GameRoomManager(
            new GradingEngine(), new GameBalance(), mapper, mock(GameSnapshotStore.class), writer);
    private long now = 5_000_000L;

    private static final class Wire {
        final List<JsonNode> received = new ArrayList<>();
        boolean closed;
        final WebSocketSession session = mock(WebSocketSession.class);

        Wire(ObjectMapper mapper) throws Exception {
            org.mockito.Mockito.when(session.getAttributes()).thenReturn(new HashMap<>());
            org.mockito.Mockito.when(session.isOpen()).thenReturn(true);
            org.mockito.Mockito.doAnswer(call -> {
                WebSocketMessage<?> message = call.getArgument(0);
                received.add(mapper.readTree(((TextMessage) message).getPayload()));
                return null;
            }).when(session).sendMessage(any());
            org.mockito.Mockito.doAnswer(call -> {
                closed = true;
                return null;
            }).when(session).close(any());
        }

        List<String> types() {
            return received.stream().map(n -> n.path("type").asString("")).toList();
        }

        JsonNode last(String type) {
            for (int i = received.size() - 1; i >= 0; i--) {
                if (received.get(i).path("type").asString("").equals(type)) return received.get(i).path("data");
            }
            return null;
        }
    }

    private Room room() {
        Room room = new Room();
        room.id = "room-1";
        room.tenantId = "t";
        room.hostUserId = "teacher";
        room.durationMinutes = 5;
        room.rules = new Rules(false, false, true, true, true, "LESSON");
        room.roster.put("s1", "An");
        for (int i = 0; i < 3; i++) {
            Question q = new Question();
            q.id = "q" + i;
            q.kind = "single_choice";
            q.points = BigDecimal.ONE;
            q.title = "Q" + i;
            q.answerKey = Map.of("correctChoiceId", "a");
            q.payload = Map.of("id", q.id, "choices", List.of(Map.of("id", "a"), Map.of("id", "b")));
            room.questions.add(q);
        }
        manager.register(room);
        return room;
    }

    private void tick(Room room) {
        manager.locked(room, at -> manager.tick(room, at));
    }

    private void send(Room room, Wire wire, String json) {
        manager.onMessage(room, (Conn) wire.session.getAttributes().get("conn"), json);
    }

    @Test
    void fullGameOverTheSocketLogic() throws Exception {
        manager.clock = () -> now;
        Room room = room();
        Wire host = new Wire(mapper);
        Wire student = new Wire(mapper);
        manager.connect(host.session, room, "teacher");
        manager.connect(student.session, room, "s1");
        assertThat(student.last("room.state").path("screen").asString("")).isEqualTo("LOBBY");
        manager.locked(room, at -> manager.tick(room, at));
        assertThat(host.last("room.lobby").path("classSize").asInt()).isEqualTo(1);
        assertThat(host.last("room.lobby").path("count").asInt()).isEqualTo(1);
        assertThat(student.last("room.state").path("question").isMissingNode()).isTrue();

        // Only the host may start.
        send(room, student, "{\"type\":\"host.start\",\"data\":{}}");
        assertThat(student.last("error").path("code").asString("")).isEqualTo("HOST_ONLY");
        send(room, host, "{\"type\":\"host.start\",\"data\":{}}");
        assertThat(student.types()).contains("room.countdown");

        now += 3_100;
        tick(room);
        assertThat(room.status).isEqualTo(Status.RUNNING);
        JsonNode question = student.last("room.state").path("question").path("question");
        assertThat(question.path("id").asString("")).isEqualTo("q0");
        assertThat(question.toString()).doesNotContain("correctChoiceId");

        now += 1_100;
        send(room, student, "{\"type\":\"answer\",\"data\":{\"questionId\":\"q0\",\"answer\":{\"choiceId\":\"a\"},\"clientMs\":900}}");
        JsonNode result = student.last("answer.result");
        assertThat(result.path("correct").asBoolean()).isTrue();
        assertThat(result.path("starsGained").asInt()).isEqualTo(110);
        assertThat(student.types()).contains("loot.offer");

        send(room, student, "{\"type\":\"loot.pick\",\"data\":{\"slot\":2}}");
        assertThat(student.last("loot.result").path("slots")).hasSize(3);
        send(room, student, "{\"type\":\"next\",\"data\":{}}");
        assertThat(student.last("question").path("question").path("id").asString("")).isEqualTo("q1");

        // Leaderboard is throttled to one a second and reaches the host too.
        tick(room);
        assertThat(student.last("leaderboard").path("me").path("total").asInt()).isGreaterThanOrEqualTo(110);
        assertThat(host.last("leaderboard").path("top")).hasSize(1);
        assertThat(host.last("leaderboard").path("top").get(0).path("firewall").asBoolean(true)).isFalse();
        int boards = (int) host.types().stream().filter("leaderboard"::equals).count();
        tick(room);
        assertThat(host.types().stream().filter("leaderboard"::equals).count()).isEqualTo(boards);

        now += 1_100;
        send(room, student, "{\"type\":\"answer\",\"data\":{\"questionId\":\"q1\",\"answer\":{\"choiceId\":\"b\"}}}");
        send(room, student, "{\"type\":\"next\",\"data\":{}}");

        // The student opens a second socket: the old socket is closed.
        Wire second = new Wire(mapper);
        manager.connect(second.session, room, "s1");
        assertThat(student.closed).isTrue();
        assertThat(student.last("error").path("code").asString("")).isEqualTo("REPLACED");
        assertThat(second.last("room.state").path("me").path("stars").asInt()).isEqualTo(110);

        // Time is up: the room ends and the result is sent and queued for the batch write.
        now += 5 * 60_000;
        tick(room);
        assertThat(room.status).isEqualTo(Status.ENDED);
        JsonNode ended = second.last("room.ended");
        assertThat(ended.path("final")).hasSize(1);
        assertThat(ended.path("me").path("rank").asInt()).isEqualTo(1);
        JsonNode weak = ended.path("me").path("weakQuestions");
        assertThat(weak).hasSize(1);
        assertThat(weak.get(0).path("questionId").asString("")).isEqualTo("q1");
        assertThat(weak.get(0).path("index").asInt(-1)).isEqualTo(1);
        assertThat(weak.get(0).path("wrongCount").asInt()).isEqualTo(1);
        verify(writer).enqueue(room);

        // Messages sent after the end change nothing and cause no second write.
        send(room, second, "{\"type\":\"answer\",\"data\":{\"questionId\":\"q1\",\"answer\":{\"choiceId\":\"a\"}}}");
        assertThat(second.last("error").path("code").asString("")).isEqualTo("ROOM_NOT_RUNNING");
        tick(room);
        verify(writer, org.mockito.Mockito.times(1)).enqueue(room);
    }

    @Test
    void strangersAreRejectedAndRoomCodesAreUniqueFourDigits() throws Exception {
        Room first = room();
        Room second = room();
        assertThat(first.code).matches("\\d{4}").isNotEqualTo(second.code);
        Wire stranger = new Wire(mapper);
        manager.connect(stranger.session, first, "not-in-class");
        assertThat(stranger.last("error").path("code").asString("")).isEqualTo("NOT_IN_CLASS");
        assertThat(stranger.closed).isTrue();
        verify(writer, never()).enqueue(any());
    }

    private Wire join(Room room, String userId) throws Exception {
        Wire wire = new Wire(mapper);
        manager.connect(wire.session, room, userId);
        return wire;
    }

    private void start(Room room, Wire host) {
        send(room, host, "{\"type\":\"host.start\",\"data\":{}}");
        now += 3_100;
        tick(room);
    }

    private static String answer(String questionId, String choice) {
        return "{\"type\":\"answer\",\"data\":{\"questionId\":\"" + questionId + "\",\"answer\":{\"choiceId\":\""
                + choice + "\"}}}";
    }

    @Test
    void lobbyMissingListIsForTheHostOnly() throws Exception {
        manager.clock = () -> now;
        Room room = room();
        room.roster.put("s2", "Binh");
        Wire host = join(room, "teacher");
        Wire student = join(room, "s1");
        tick(room);
        JsonNode hostLobby = host.last("room.lobby");
        assertThat(hostLobby.path("missing")).hasSize(1);
        assertThat(hostLobby.path("missing").get(0).path("userId").asString("")).isEqualTo("s2");
        assertThat(hostLobby.path("missing").get(0).path("displayName").asString("")).isEqualTo("Binh");
        assertThat(student.last("room.lobby").path("missing").isArray()).isTrue();
        assertThat(student.last("room.lobby").path("missing")).isEmpty();
        assertThat(host.last("room.state").path("lobby").path("missing")).hasSize(2); // snapshot taken before s1 joined
        assertThat(student.last("room.state").path("lobby").path("missing")).isEmpty();
    }

    @Test
    void hostSeesTopEightAndStudentsTopFive() throws Exception {
        manager.clock = () -> now;
        Room room = room();
        Wire host = join(room, "teacher");
        List<Wire> students = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            room.roster.put("p" + i, "P" + i);
            students.add(join(room, "p" + i));
        }
        start(room, host);
        now += 1_100;
        for (int i = 0; i < 9; i++) send(room, students.get(i), answer("q0", "a"));
        tick(room);
        assertThat(host.last("leaderboard").path("top")).hasSize(8);
        assertThat(students.get(0).last("leaderboard").path("top")).hasSize(5);
        assertThat(host.last("room.state").path("leaderboard").path("top")).hasSize(8);
        assertThat(students.get(0).last("room.state").path("leaderboard").path("top")).hasSize(5);
    }

    @Test
    void classStatsCountPlayersAndSortByWrongPlayers() throws Exception {
        manager.clock = () -> now;
        Room room = room();
        room.roster.put("s2", "Binh");
        Wire host = join(room, "teacher");
        Wire s1 = join(room, "s1");
        Wire s2 = join(room, "s2");
        start(room, host);
        now += 1_100;
        send(room, s1, answer("q0", "b"));
        send(room, s2, answer("q0", "a"));
        send(room, s1, "{\"type\":\"next\",\"data\":{}}");
        send(room, s2, "{\"type\":\"loot.pick\",\"data\":{\"slot\":0}}");
        send(room, s2, "{\"type\":\"next\",\"data\":{}}");
        now += 1_100;
        send(room, s1, answer("q1", "b"));
        send(room, s2, answer("q1", "b"));
        now += 2_100;
        tick(room);
        JsonNode stats = host.last("class.stats");
        assertThat(stats.path("perQuestion").get(0).path("questionId").asString("")).isEqualTo("q1");
        assertThat(stats.path("perQuestion").get(0).path("wrongPlayers").asInt()).isEqualTo(2);
        assertThat(stats.path("perQuestion").get(0).path("answeredPlayers").asInt()).isEqualTo(2);
        assertThat(stats.path("perQuestion").get(1).path("questionId").asString("")).isEqualTo("q0");
        assertThat(stats.path("perQuestion").get(1).path("wrongPlayers").asInt()).isEqualTo(1);
        assertThat(stats.path("perQuestion").get(1).path("answeredPlayers").asInt()).isEqualTo(2);
        assertThat(stats.path("playerCount").asInt()).isEqualTo(2);
        assertThat(stats.path("playersOnline").asInt()).isEqualTo(2);
        assertThat(stats.path("avgProgress").asDouble()).isEqualTo(0.5);
        assertThat(stats.path("questionTotal").asInt()).isEqualTo(3);
        assertThat(stats.path("firewallOn").asInt()).isZero();
        assertThat(stats.path("hackWins").asInt()).isZero();
    }

    @Test
    void roomSnapshotSurvivesAJsonRoundTrip() throws Exception {
        Room room = room();
        room.quizId = "quiz-1";
        room.questionSet = "CLASS_WRONG";
        room.questionStats.put("q0", new int[] {3, 1});
        Room copy = mapper.readValue(mapper.writeValueAsString(room), Room.class);
        assertThat(copy.id).isEqualTo(room.id);
        assertThat(copy.quizId).isEqualTo("quiz-1");
        assertThat(copy.questionSet).isEqualTo("CLASS_WRONG");
        assertThat(copy.questions).hasSize(3);
        assertThat(copy.questions.get(0).answerKey).containsEntry("correctChoiceId", "a");
        assertThat(copy.rules).isEqualTo(room.rules);
        assertThat(copy.questionStats.get("q0")).containsExactly(3, 1);
        assertThat(copy.lock).isNotNull();
        assertThat(copy.connections).isEmpty();
    }
}

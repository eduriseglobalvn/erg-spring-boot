package com.erg.ergspringboot.lms.internal.game;

import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.lms.internal.game.GameEngine.GameException;
import com.erg.ergspringboot.lms.internal.game.GameModel.Conn;
import com.erg.ergspringboot.lms.internal.game.GameModel.FeedItem;
import com.erg.ergspringboot.lms.internal.game.GameModel.Out;
import com.erg.ergspringboot.lms.internal.game.GameModel.Player;
import com.erg.ergspringboot.lms.internal.game.GameModel.Room;
import com.erg.ergspringboot.lms.internal.game.GameModel.Screen;
import com.erg.ergspringboot.lms.internal.game.GameModel.Status;
import com.erg.ergspringboot.lms.internal.quiz.runtime.GradingEngine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Owns every live room: the in-memory map, the WebSocket fan-out, the 250 ms clock and the Redis snapshots.
 *
 * <p>Each room is handled one message at a time under its own lock; messages are only collected under the lock
 * and written after it is released, so a slow client cannot stall a room. Postgres is never touched here.
 *
 * <p>shortcut: one backend instance holds all rooms; with several instances move to Redis pub/sub plus sticky
 * sessions by roomId.
 */
@Component
public class GameRoomManager {

    private static final Logger LOG = LoggerFactory.getLogger(GameRoomManager.class);
    private static final long ENDED_KEEP_MS = 10 * 60_000L;
    private static final long SNAPSHOT_EVERY_MS = 3_000L;
    private static final int MAX_MESSAGE_CHARS = 16_384;

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> codes = new ConcurrentHashMap<>();
    private final GameEngine engine;
    private final GameBalance balance;
    private final ObjectMapper mapper;
    private final GameSnapshotStore store;
    private final GameResultWriter writer;
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "game-ticker");
        thread.setDaemon(true);
        return thread;
    });
    LongSupplier clock = System::currentTimeMillis;

    public GameRoomManager(
            GradingEngine grading,
            GameBalance balance,
            ObjectMapper mapper,
            GameSnapshotStore store,
            GameResultWriter writer
    ) {
        this.balance = balance;
        this.engine = new GameEngine(balance, grading::grade, ThreadLocalRandom.current());
        this.mapper = mapper;
        this.store = store;
        this.writer = writer;
    }

    @PostConstruct
    void start() {
        restore();
        ticker.scheduleWithFixedDelay(this::tickAll, 250, 250, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        ticker.shutdownNow();
    }

    // ---- registry -------------------------------------------------------------------------------------------

    /** Assigns a 4-digit code unique among open rooms and registers the room. */
    public Room register(Room room) {
        if (openCount() >= balance.getMaxOpenRooms()) throw new ConflictException("Too many open game rooms");
        for (int attempt = 0; attempt < 100; attempt++) {
            String code = String.format("%04d", ThreadLocalRandom.current().nextInt(10_000));
            if (codes.putIfAbsent(code, room.id) == null) {
                room.code = code;
                rooms.put(room.id, room);
                room.stateDirty = true;
                return room;
            }
        }
        throw new ConflictException("No free room code, try again shortly");
    }

    public Room get(String roomId) {
        return roomId == null ? null : rooms.get(roomId);
    }

    public Room byCode(String code) {
        String id = code == null ? null : codes.get(code);
        return id == null ? null : rooms.get(id);
    }

    public Collection<Room> all() {
        return rooms.values();
    }

    private long openCount() {
        return rooms.values().stream().filter(Room::open).count();
    }

    /** Runs {@code body} under the room lock, then writes the messages it queued. */
    public void locked(Room room, LongConsumer body) {
        List<Out> out;
        room.lock.lock();
        try {
            body.accept(clock.getAsLong());
        } finally {
            out = room.outbox;
            room.outbox = new ArrayList<>();
            room.lock.unlock();
        }
        flush(out);
    }

    private void flush(List<Out> out) {
        for (Out message : out) {
            try {
                if (message.json() == null) {
                    message.conn().session().close(CloseStatus.POLICY_VIOLATION);
                } else {
                    message.conn().session().sendMessage(new TextMessage(message.json()));
                }
            } catch (IOException | RuntimeException failure) {
                LOG.debug("Game socket write failed: {}", failure.toString());
            }
        }
    }

    // ---- sockets --------------------------------------------------------------------------------------------

    /** Attaches a socket; a newer connection of the same user replaces (kicks) the older one. */
    public void connect(WebSocketSession raw, Room room, String userId) {
        boolean host = userId.equals(room.hostUserId);
        Conn conn = new Conn(userId, host, new ConcurrentWebSocketSessionDecorator(raw, 5_000, 128 * 1024));
        raw.getAttributes().put("conn", conn);
        locked(room, now -> {
            try {
                if (!host) engine.join(room, userId, now);
            } catch (GameException rejected) {
                send(room, conn, "error", error(rejected));
                room.outbox.add(new Out(conn, null));
                return;
            }
            Conn old = room.connections.put(userId, conn);
            if (old != null) {
                send(room, old, "error", Map.of("code", "REPLACED", "message", "Opened on another device"));
                room.outbox.add(new Out(old, null));
            }
            send(room, conn, "room.state", GameViews.state(room, conn, now));
            room.lobbyDirty = true;
        });
    }

    public void disconnect(Room room, Conn conn) {
        locked(room, now -> {
            if (room.connections.remove(conn.userId(), conn)) room.lobbyDirty = true;
        });
    }

    public void onMessage(Room room, Conn conn, String text) {
        if (text.length() > MAX_MESSAGE_CHARS) {
            locked(room, now -> send(room, conn, "error", Map.of("code", "TOO_LARGE", "message", "Message too large")));
            return;
        }
        locked(room, now -> {
            try {
                JsonNode tree = mapper.readTree(text);
                handle(room, conn, tree.path("type").asString(""), tree.path("data"), now);
            } catch (GameException rejected) {
                send(room, conn, "error", error(rejected));
            } catch (RuntimeException failure) {
                LOG.warn("Game message failed in room {}: {}", room.id, failure.toString());
                send(room, conn, "error", Map.of("code", "BAD_MESSAGE", "message", "Could not process the message"));
            }
        });
    }

    private void handle(Room room, Conn conn, String type, JsonNode data, long now) {
        switch (type) {
            case "resume" -> send(room, conn, "room.state", GameViews.state(room, conn, now));
            case "answer" -> {
                Player p = player(room, conn);
                Map<String, Object> result = engine.answer(room, p, data.path("questionId").asString(""),
                        mapper.convertValue(data.path("answer"), Object.class), now);
                send(room, conn, "answer.result", result);
                if (p.screen == Screen.LOOT_PICK) send(room, conn, "loot.offer", Map.of("slots", 3));
            }
            case "loot.pick" -> {
                Player p = player(room, conn);
                send(room, conn, "loot.result", engine.pick(room, p, data.path("slot").asInt(-1), now));
            }
            case "next" -> {
                Player p = player(room, conn);
                if (engine.next(room, p, now)) {
                    if (p.screen == Screen.QUESTION) send(room, conn, "question", GameViews.questionMessage(room, p));
                    else send(room, conn, "room.state", GameViews.state(room, conn, now));
                }
            }
            case "host.start" -> {
                requireHost(conn);
                engine.startCountdown(room, now);
                broadcast(room, "room.countdown", Map.of("startsAt", room.countdownEndsAt, "serverNow", now));
                broadcast(room, "room.phase", GameViews.phase(room));
            }
            case "host.pause" -> {
                requireHost(conn);
                engine.pause(room, now);
                broadcast(room, "room.phase", GameViews.phase(room));
            }
            case "host.resume" -> {
                requireHost(conn);
                engine.resume(room, now);
                broadcast(room, "room.phase", GameViews.phase(room));
            }
            case "host.end" -> {
                requireHost(conn);
                engine.requestEnd(room, now);
                closeOut(room, now);
            }
            default -> throw new GameException("UNKNOWN_TYPE", "Unknown message type");
        }
    }

    private Player player(Room room, Conn conn) {
        Player p = room.players.get(conn.userId());
        if (conn.host() || p == null) throw new GameException("NOT_A_PLAYER", "Only players can do this");
        return p;
    }

    private static void requireHost(Conn conn) {
        if (!conn.host()) throw new GameException("HOST_ONLY", "Only the host can do this");
    }

    private static Map<String, Object> error(GameException e) {
        return Map.of("code", e.code(), "message", String.valueOf(e.getMessage()));
    }

    // ---- clock ----------------------------------------------------------------------------------------------

    private void tickAll() {
        for (Room room : rooms.values()) {
            try {
                locked(room, now -> tick(room, now));
            } catch (RuntimeException failure) {
                LOG.error("Game tick failed for room {}", room.id, failure);
            }
        }
    }

    void tick(Room room, long now) {
        if (!room.open()) {
            if (now - room.endedAt > ENDED_KEEP_MS) evict(room);
            return;
        }
        if (engine.tick(room, now)) {
            if (room.status == Status.RUNNING) {
                broadcast(room, "room.phase", GameViews.phase(room));
                for (Conn c : room.connections.values()) send(room, c, "room.state", GameViews.state(room, c, now));
            } else if (room.status == Status.ENDING) {
                closeOut(room, now);
            }
        }
        if (!room.open()) return;
        if (room.lobbyDirty) {
            room.lobbyDirty = false;
            broadcast(room, "room.lobby", GameViews.lobby(room));
        }
        boolean live = room.status == Status.RUNNING || room.status == Status.PAUSED;
        if (live && room.boardDirty && now - room.lastBoardAt >= 1000) {
            room.boardDirty = false;
            room.lastBoardAt = now;
            sendLeaderboards(room);
        }
        if (!room.feed.isEmpty() && now - room.lastFeedAt >= 1000) {
            room.lastFeedAt = now;
            List<FeedItem> batch = new ArrayList<>(room.feed.subList(0, Math.min(2, room.feed.size())));
            room.feed.subList(0, batch.size()).clear();
            List<Map<String, Object>> items = new ArrayList<>();
            for (FeedItem item : batch) items.add(Map.of("at", item.at(), "text", item.text(), "tone", item.tone()));
            broadcast(room, "feed", Map.of("items", items));
        }
        if (live && now - room.lastStatsAt >= 2000) {
            room.lastStatsAt = now;
            for (Conn c : room.connections.values()) {
                if (c.host()) send(room, c, "class.stats", GameViews.classStats(room, onlinePlayers(room)));
            }
        }
        if (room.stateDirty && now - room.lastSnapshotAt >= SNAPSHOT_EVERY_MS) snapshot(room, now);
    }

    /** ENDING (or cancelled) to the final result: broadcasts phases and {@code room.ended}, queues the DB write. */
    private void closeOut(Room room, long now) {
        if (room.status == Status.ENDING) {
            broadcast(room, "room.phase", GameViews.phase(room));
            engine.finish(room, now);
        }
        if (room.status != Status.ENDED && room.status != Status.CANCELLED) return;
        broadcast(room, "room.phase", GameViews.phase(room));
        for (Conn c : room.connections.values()) send(room, c, "room.ended", GameViews.ended(room, c));
        codes.remove(room.code, room.id);
        if (!room.persisted) {
            room.persisted = true;
            writer.enqueue(room);
        }
        snapshot(room, now);
    }

    private void evict(Room room) {
        rooms.remove(room.id);
        codes.remove(room.code, room.id);
        for (Conn c : room.connections.values()) room.outbox.add(new Out(c, null));
        store.delete(room.id);
    }

    private void snapshot(Room room, long now) {
        room.lastSnapshotAt = now;
        room.stateDirty = false;
        store.save(room);
    }

    private void restore() {
        for (Room room : store.loadAll()) {
            rooms.put(room.id, room);
            if (room.open()) codes.put(room.code, room.id);
            room.persisted = !room.open();
        }
    }

    // ---- message building -----------------------------------------------------------------------------------

    private String envelope(Room room, String type, Object data) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", type);
        message.put("seq", ++room.seq);
        message.put("data", data);
        return mapper.writeValueAsString(message);
    }

    private void send(Room room, Conn conn, String type, Object data) {
        room.outbox.add(new Out(conn, envelope(room, type, data)));
    }

    private void broadcast(Room room, String type, Object data) {
        String json = envelope(room, type, data);
        for (Conn c : room.connections.values()) room.outbox.add(new Out(c, json));
    }

    private void sendLeaderboards(Room room) {
        List<Map<String, Object>> top = GameViews.top(room, 5);
        for (Conn c : room.connections.values()) {
            Player p = room.players.get(c.userId());
            if (!c.host() && (!room.rules.showLeaderboard() || p == null)) continue;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("top", top);
            if (p != null) data.put("me", Map.of("rank", GameViews.rank(room, p), "total", p.total()));
            send(room, c, "leaderboard", data);
        }
    }

    private int onlinePlayers(Room room) {
        return (int) room.connections.values().stream().filter(c -> !c.host()).count();
    }
}

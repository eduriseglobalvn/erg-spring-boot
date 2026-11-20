package com.erg.ergspringboot.lms.internal.game;

import com.erg.ergspringboot.lms.internal.game.GameModel.Room;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Room snapshots in the Redis state store (TTL 2 h) so a restart does not lose a running game. Never throws. */
@Component
public class GameSnapshotStore {

    private static final Logger LOG = LoggerFactory.getLogger(GameSnapshotStore.class);
    private static final String KEY_PREFIX = "erg:games:room:";
    private static final Duration TTL = Duration.ofHours(2);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public GameSnapshotStore(@Qualifier("stateStringRedisTemplate") ObjectProvider<StringRedisTemplate> redis,
                             ObjectMapper mapper) {
        this.redis = redis.getIfAvailable();
        this.mapper = mapper;
    }

    void save(Room room) {
        if (redis == null) return;
        try {
            redis.opsForValue().set(KEY_PREFIX + room.id, mapper.writeValueAsString(room), TTL);
        } catch (RuntimeException failure) {
            LOG.warn("Game snapshot failed for room {}: {}", room.id, failure.toString());
        }
    }

    void delete(String roomId) {
        if (redis == null) return;
        try {
            redis.delete(KEY_PREFIX + roomId);
        } catch (RuntimeException failure) {
            LOG.debug("Could not drop game snapshot: {}", failure.toString());
        }
    }

    List<Room> loadAll() {
        List<Room> rooms = new ArrayList<>();
        if (redis == null) return rooms;
        try {
            Set<String> keys = redis.keys(KEY_PREFIX + "*");
            if (keys == null) return rooms;
            for (String key : keys) {
                String json = redis.opsForValue().get(key);
                if (json != null) rooms.add(mapper.readValue(json, Room.class));
            }
            LOG.info("Restored {} game room(s) from Redis", rooms.size());
        } catch (RuntimeException failure) {
            LOG.warn("Could not restore game rooms: {}", failure.toString());
        }
        return rooms;
    }
}

package com.erg.ergspringboot.service.quiz.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonbSchemaMigratorTest {

    @Test
    void upgradesV1PayloadToLatestShape() {
        JsonbSchemaMigrator migrator = JsonbSchemaMigrator.forType("quizDraftTree")
                .step(1, payload -> {
                    // v1 -> v2: rename "name" to "title"
                    Map<String, Object> next = new LinkedHashMap<>(payload);
                    next.put("title", next.remove("name"));
                    return next;
                })
                .step(2, payload -> {
                    // v2 -> v3: add optional "locale" defaulting to "vi"
                    Map<String, Object> next = new LinkedHashMap<>(payload);
                    next.putIfAbsent("locale", "vi");
                    return next;
                })
                .build();

        Map<String, Object> v1 = new LinkedHashMap<>();
        v1.put("schemaVersion", 1);
        v1.put("name", "Bài kiểm tra");

        Map<String, Object> result = migrator.migrateToLatest(v1);

        assertThat(result.get("schemaVersion")).isEqualTo(3);
        assertThat(result.get("title")).isEqualTo("Bài kiểm tra");
        assertThat(result).doesNotContainKey("name");
        assertThat(result.get("locale")).isEqualTo("vi");
    }

    @Test
    void treatsMissingVersionAsVersionOne() {
        JsonbSchemaMigrator migrator = JsonbSchemaMigrator.forType("blob")
                .step(1, payload -> {
                    Map<String, Object> next = new LinkedHashMap<>(payload);
                    next.put("upgraded", true);
                    return next;
                })
                .build();

        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("value", 42);

        Map<String, Object> result = migrator.migrateToLatest(legacy);

        assertThat(result.get("schemaVersion")).isEqualTo(2);
        assertThat(result.get("upgraded")).isEqualTo(true);
        assertThat(result.get("value")).isEqualTo(42);
    }

    @Test
    void leavesCurrentVersionPayloadStampedAtTarget() {
        JsonbSchemaMigrator migrator = JsonbSchemaMigrator.forType("blob")
                .step(1, payload -> payload)
                .build();

        Map<String, Object> current = new LinkedHashMap<>();
        current.put("schemaVersion", 2);
        current.put("keep", "me");

        Map<String, Object> result = migrator.migrateToLatest(current);

        assertThat(result.get("schemaVersion")).isEqualTo(2);
        assertThat(result.get("keep")).isEqualTo("me");
    }

    @Test
    void passesThroughNull() {
        JsonbSchemaMigrator migrator = JsonbSchemaMigrator.forType("blob")
                .step(1, payload -> payload)
                .build();

        assertThat(migrator.migrateToLatest(null)).isNull();
    }

    @Test
    void rejectsPayloadNewerThanTarget() {
        JsonbSchemaMigrator migrator = JsonbSchemaMigrator.forType("blob")
                .step(1, payload -> payload)
                .build();

        Map<String, Object> future = new LinkedHashMap<>();
        future.put("schemaVersion", 99);

        assertThatThrownBy(() -> migrator.migrateToLatest(future))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("newer than supported target");
    }

    @Test
    void rejectsNonContiguousStepChain() {
        assertThatThrownBy(() -> JsonbSchemaMigrator.forType("blob")
                .step(1, payload -> payload)
                .step(3, payload -> payload)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contiguous chain");
    }

    @Test
    void doesNotMutateInputPayload() {
        JsonbSchemaMigrator migrator = JsonbSchemaMigrator.forType("blob")
                .step(1, payload -> {
                    Map<String, Object> next = new LinkedHashMap<>(payload);
                    next.put("added", true);
                    return next;
                })
                .build();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("schemaVersion", 1);

        migrator.migrateToLatest(input);

        assertThat(input).doesNotContainKey("added");
        assertThat(input.get("schemaVersion")).isEqualTo(1);
    }
}

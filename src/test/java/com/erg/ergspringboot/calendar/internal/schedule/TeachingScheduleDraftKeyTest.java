package com.erg.ergspringboot.calendar.internal.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.exception.TeachingScheduleException;
import org.junit.jupiter.api.Test;

class TeachingScheduleDraftKeyTest {

    @Test
    void noKeyMeansNoIdempotency() {
        assertThat(TeachingScheduleDraftService.storedKey("u1", null)).isNull();
        assertThat(TeachingScheduleDraftService.storedKey("u1", "  ")).isNull();
    }

    @Test
    void theStoredKeyIsScopedToTheActorAndKeptApartFromBulkKeys() {
        assertThat(TeachingScheduleDraftService.storedKey("u1", " k1 ")).isEqualTo("draft:u1:k1");
        assertThat(TeachingScheduleDraftService.storedKey("u2", "k1")).isNotEqualTo(TeachingScheduleDraftService.storedKey("u1", "k1"));
    }

    @Test
    void aKeyOver100CharactersIsRefused() {
        assertThatThrownBy(() -> TeachingScheduleDraftService.storedKey("u1", "x".repeat(101)))
                .isInstanceOf(TeachingScheduleException.class)
                .hasFieldOrPropertyWithValue("code", "IDEMPOTENCY_KEY_TOO_LONG");
    }
}

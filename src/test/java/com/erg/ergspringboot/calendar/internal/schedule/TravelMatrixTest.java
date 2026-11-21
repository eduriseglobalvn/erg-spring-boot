package com.erg.ergspringboot.calendar.internal.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.internal.schedule.TravelTimeService.TravelMatrix;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TravelMatrixTest {

    private final TravelMatrix matrix = new TravelMatrix(Map.of("a|b", 20), 50);

    @Test
    void pairIsFoundInEitherOrder() {
        assertThat(matrix.requiredMinutes("a", "b")).isEqualTo(20);
        assertThat(matrix.requiredMinutes("b", "a")).isEqualTo(20);
    }

    @Test
    void unknownPairFallsBackToTheDefaultAndTheSameSchoolIsZero() {
        assertThat(matrix.requiredMinutes("a", "c")).isEqualTo(50);
        assertThat(matrix.requiredMinutes("a", "a")).isZero();
    }
}

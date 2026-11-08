package com.erg.ergspringboot.timekeeping.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExplanationSuggesterTest {

    private static final LocalTime END = LocalTime.of(11, 30);

    @Test
    void earlierPeriodEndKeepsShiftEnd() {
        assertThat(ExplanationSuggester.suggestOut(END, Optional.of(LocalTime.of(11, 15)))).isEqualTo(END);
    }

    @Test
    void laterPeriodEndWins() {
        assertThat(ExplanationSuggester.suggestOut(END, Optional.of(LocalTime.of(11, 45))))
                .isEqualTo(LocalTime.of(11, 45));
    }

    @Test
    void noPeriodUsesShiftEnd() {
        assertThat(ExplanationSuggester.suggestOut(END, Optional.empty())).isEqualTo(END);
    }
}

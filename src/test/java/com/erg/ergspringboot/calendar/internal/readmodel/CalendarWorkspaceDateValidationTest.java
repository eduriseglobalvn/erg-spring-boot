package com.erg.ergspringboot.calendar.internal.readmodel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.exception.BadRequestException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A malformed calendar date is a client error (400 / GraphQL BAD_REQUEST), never a 500. */
class CalendarWorkspaceDateValidationTest {

    private final CalendarWorkspaceQueryUseCase useCase =
            new CalendarWorkspaceQueryUseCase(null, null, null, null, null, null, null, null, null);

    @Test
    void lcmsRejectsAMalformedDate() {
        assertThatThrownBy(() -> useCase.lcms("erg", List.of(), "2026-13-45", "2026-11-30", "month",
                List.of(), List.of(), List.of(), List.of(), List.of(), null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void teacherRejectsAMalformedDate() {
        assertThatThrownBy(() -> useCase.teacher("erg", "usr-t", "13/11/2026", "2026-11-30", "week",
                List.of(), List.of(), List.of(), List.of(), List.of(), null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void studentRejectsAMissingDateBeforeLookingUpTheStudent() {
        assertThatThrownBy(() -> useCase.student("erg", "usr-s", null, "2026-11-30", "week",
                List.of(), List.of(), List.of(), null))
                .isInstanceOf(BadRequestException.class);
    }
}

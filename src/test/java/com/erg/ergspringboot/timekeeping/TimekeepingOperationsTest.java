package com.erg.ergspringboot.timekeeping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PunchCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PunchResult;
import com.erg.ergspringboot.timekeeping.internal.day.DayAggregationService;
import com.erg.ergspringboot.timekeeping.internal.evidence.FaceConsentService;
import com.erg.ergspringboot.timekeeping.internal.evidence.LocalPrivateObjectStorage;
import com.erg.ergspringboot.timekeeping.internal.me.SelfViewService;
import com.erg.ergspringboot.timekeeping.internal.punch.PunchService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class TimekeepingOperationsTest {

    private final PunchService punches = mock(PunchService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<LocalPrivateObjectStorage> noLocalStore = mock(ObjectProvider.class);
    private final TimekeepingOperations operations = new TimekeepingOperations(punches, mock(SelfViewService.class),
            mock(DayAggregationService.class), mock(FaceConsentService.class), noLocalStore, Clock.systemUTC());
    private final ErgPrincipal teacher = new ErgPrincipal("u-1", "s", "tenant-a", List.of(), List.of(), List.of(),
            List.of(), "staff", "user", Map.of());
    private final PunchCommand command = new PunchCommand(PunchDirection.IN, 21.0, 105.0, 10.0, false, null, null);

    @Test
    void losingTheSameKeyInsertRaceReturnsTheWinningPunch() {
        PunchResult winner = new PunchResult(PunchStatus.VALID, null, false, 0, null, null);
        when(punches.punch("tenant-a", "u-1", "key-1", command)).thenThrow(new DataIntegrityViolationException("dup"));
        when(punches.replay("tenant-a", "u-1", "key-1")).thenReturn(Optional.of(winner));

        assertThat(operations.punch(teacher, "key-1", command)).isSameAs(winner);
    }

    @Test
    void anIntegrityErrorThatIsNotTheSameKeyIsRethrown() {
        when(punches.punch(any(), any(), any(), any())).thenThrow(new DataIntegrityViolationException("other"));
        when(punches.replay("tenant-a", "u-1", "key-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> operations.punch(teacher, "key-1", command))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void signedPhotoIsNotFoundWhenPhotosAreNotStoredLocally() {
        assertThatThrownBy(() -> operations.signedPhoto("private/timekeeping/t/a.jpg", 1L, "sig"))
                .isInstanceOfSatisfying(TimekeepingException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void monthRejectsAMalformedPeriod() {
        assertThatThrownBy(() -> operations.month(teacher, "2026-13"))
                .isInstanceOfSatisfying(TimekeepingException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}

package com.erg.ergspringboot.timekeeping.internal.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.timekeeping.DayPart;
import com.erg.ergspringboot.timekeeping.LeaveKind;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.LeavePreview;
import com.erg.ergspringboot.timekeeping.internal.schedule.TeacherPeriodReader;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkCodeCounterRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkDayRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestEvidenceRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestStepRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Leave preview across year-end uses the same per-year balance as submit. */
class RequestServiceTest {

    private static final String T = "tenant-a";
    private static final String U = "teacher-1";
    private static final String S = "school-1";

    private final LeaveBalanceService balance = mock(LeaveBalanceService.class);
    private final WorkContextResolver context = mock(WorkContextResolver.class);
    private final ApprovalChainResolver chains = mock(ApprovalChainResolver.class);
    private final AffectedPeriodsService affected = mock(AffectedPeriodsService.class);
    private final Clock clock = Clock.systemUTC();
    private final RequestRules rules = new RequestRules(context, balance, mock(TkRequestRepository.class),
            mock(TkDayRepository.class), mock(TkPunchRepository.class), clock);
    private final RequestService service = new RequestService(mock(TkRequestRepository.class),
            mock(TkRequestStepRepository.class), mock(TkRequestEvidenceRepository.class),
            mock(TkCodeCounterRepository.class), rules, chains, mock(RequestNotifier.class),
            mock(RequestViewAssembler.class), balance, affected, mock(TeacherPeriodReader.class), context, clock);

    @Test
    void decemberToJanuaryShowsTheYearWithTheLeastBalanceLeft() {
        LocalDate from = LocalDate.of(2026, 12, 30);
        LocalDate to = LocalDate.of(2027, 1, 5);
        when(context.schoolOf(T, U)).thenReturn(Optional.of(S));
        when(affected.affected(eq(T), eq(U), any(), any(), any())).thenReturn(List.of());
        when(chains.resolve(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of());
        when(chains.refs(eq(T), any())).thenReturn(List.of());
        when(balance.countDays(T, S, from, to, DayPart.FULL)).thenReturn(new BigDecimal("4"));
        when(balance.countDays(T, S, from, LocalDate.of(2026, 12, 31), DayPart.FULL)).thenReturn(new BigDecimal("2"));
        when(balance.countDays(T, S, LocalDate.of(2027, 1, 1), to, DayPart.FULL)).thenReturn(new BigDecimal("2"));
        when(balance.availableAnnual(T, U, 2026, null)).thenReturn(new BigDecimal("1.5"));
        when(balance.availableAnnual(T, U, 2027, null)).thenReturn(new BigDecimal("12"));

        LeavePreview annual = service.previewLeave(T, U, from, to, DayPart.FULL, LeaveKind.ANNUAL);
        assertThat(annual.days()).isEqualByComparingTo("4");
        assertThat(annual.balanceBefore()).isEqualByComparingTo("1.5"); // 2026, not 2027's 12
        assertThat(annual.balanceAfter()).isEqualByComparingTo("-0.5"); // only 2026's 2 days come out of 2026

        LeavePreview sick = service.previewLeave(T, U, from, to, DayPart.FULL, LeaveKind.SICK);
        assertThat(sick.balanceBefore()).isEqualByComparingTo(sick.balanceAfter());
    }
}

package com.erg.ergspringboot.timekeeping.internal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.exception.ApiException;
import com.erg.ergspringboot.timekeeping.PeriodStage;
import com.erg.ergspringboot.timekeeping.internal.request.RequestServiceIT.Fixture;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class PeriodLockServiceIT extends AdminITBase {

    private static final String T = "tk-lock-it";

    @Autowired PeriodLockService locks;

    private Fixture f;

    @BeforeEach
    void seed() {
        f = Fixture.seed(jdbc, T, "lk");
    }

    @Test
    void pendingRequestsInThePeriodBlockTheLock() {
        request(T, f.school(), f.teacher(), "LEAVE", "ANNUAL", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5),
                "PENDING");
        request(T, f.school(), f.lead(), "LEAVE", "SICK", LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1),
                "PENDING");
        request(T, f.school(), f.vp(), "LEAVE", "SICK", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2), "PENDING");

        assertThatThrownBy(() -> locks.lock(T, "admin", f.school(), "2026-10"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("Còn 2 đơn chưa duyệt trong kỳ");
                });
    }

    @Test
    void lockIsIdempotentAndReopenNeedsAReasonAndIsAudited() {
        assertThat(locks.lock(T, "admin", f.school(), "2026-10").stage()).isEqualTo(PeriodStage.LOCKED);
        assertThat(locks.lock(T, "admin2", f.school(), "2026-10").lockedBy()).isEqualTo("admin"); // no-op

        assertThatThrownBy(() -> locks.reopen(T, "admin", f.school(), "2026-10", " "))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        var reopened = locks.reopen(T, "admin", f.school(), "2026-10", "Sửa công của cô Lan");

        assertThat(reopened.stage()).isEqualTo(PeriodStage.REVIEW);
        flush();
        assertThat(jdbc.queryForObject("select detail->>'reason' from tk_audit_log where tenant_id = ? and action ="
                + " 'REOPEN_PERIOD'", String.class, T)).isEqualTo("Sửa công của cô Lan");
        assertThat(jdbc.queryForObject("select count(*) from tk_period_locks where tenant_id = ? and reopened_at is null",
                Integer.class, T)).isZero();
    }

    @Test
    void aMonthNotOverYetOrABadPeriodCannotBeLocked() {
        assertThatThrownBy(() -> locks.lock(T, "admin", f.school(), "2026-11"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> locks.lock(T, "admin", f.school(), "2026-1"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> locks.lock(T, "admin", "school-of-another-tenant", "2026-10"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}

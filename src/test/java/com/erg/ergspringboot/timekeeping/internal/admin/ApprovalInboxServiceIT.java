package com.erg.ergspringboot.timekeeping.internal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.exception.ApiException;
import com.erg.ergspringboot.timekeeping.ApprovalBox;
import com.erg.ergspringboot.timekeeping.DayPart;
import com.erg.ergspringboot.timekeeping.LeaveKind;
import com.erg.ergspringboot.timekeeping.RequestType;
import com.erg.ergspringboot.timekeeping.StepStatus;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.BulkApproveResult;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.Skipped;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestStepView;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestView;
import com.erg.ergspringboot.timekeeping.internal.request.RequestService;
import com.erg.ergspringboot.timekeeping.internal.request.RequestServiceIT.Fixture;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class ApprovalInboxServiceIT extends AdminITBase {

    private static final LocalDate MON = LocalDate.of(2026, 11, 16);

    @Autowired ApprovalInboxService inbox;
    @Autowired RequestService requests;

    @Test
    void anAdminApprovingTheLeadsStepActsOnBehalfAndMovesItToStepTwo() {
        String t = "tk-inbox-it";
        Fixture f = Fixture.seed(jdbc, t, "ib");
        String id = submit(t, f, LeaveKind.ANNUAL, MON, MON.plusDays(1));

        var detail = inbox.detail(principal(t, "admin", "timekeeping.manage"), id);
        assertThat(detail.actingOnBehalf()).isTrue();
        RequestView after = inbox.approve(principal(t, "admin", "timekeeping.manage"), id, null);

        assertThat(after.currentStep()).isEqualTo(2);
        RequestStepView first = after.steps().get(0);
        assertThat(first.status()).isEqualTo(StepStatus.APPROVED);
        assertThat(first.onBehalf()).isTrue();
        assertThat(first.actedBy()).isEqualTo("admin");
        flush();
        assertThat(jdbc.queryForObject("select count(*) from tk_audit_log where tenant_id = ? and action = "
                + "'APPROVE_ON_BEHALF'", Integer.class, t)).isEqualTo(1);
        // the step-2 vice principal now sees it in MINE; an unrelated approver gets 404 for the id
        assertThat(inbox.list(principal(t, f.vp(), "timekeeping.approve"), ApprovalBox.MINE, null, null, null, 0, 20)
                .total()).isEqualTo(1);
        assertThatThrownBy(() -> inbox.detail(principal(t, "stranger", "timekeeping.approve"), id))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // bulk approve commits each request on its own
    void bulkApproveSkipsTheLeaveBeyondTheBalanceAndApprovesTheOthers() {
        String t = "tk-bulk-it";
        Fixture f = transactions.execute(status -> Fixture.seed(jdbc, t, "bk"));
        try {
            String annual = submit(t, f, LeaveKind.ANNUAL, MON, MON);
            String unpaid = submit(t, f, LeaveKind.UNPAID, MON.plusDays(1), MON.plusDays(1));
            String sick = submit(t, f, LeaveKind.SICK, MON.plusDays(2), MON.plusDays(2));
            // 14 days already approved in March: the entitlement (12 + 2 for seniority) is used up
            transactions.executeWithoutResult(status -> jdbc.update("insert into tk_requests (id, tenant_id,"
                    + " school_id, code, user_id, type, leave_kind, from_date, to_date, part, days, status) values ('old-annual', ?, ?, 'DT-OLD-1', ?, 'LEAVE', 'ANNUAL',"
                    + " '2026-03-02', '2026-03-19', 'FULL', 14, 'APPROVED')", t, f.school(), f.teacher()));

            BulkApproveResult result = inbox.bulkApprove(principal(t, f.lead(), "timekeeping.approve"),
                    List.of(annual, unpaid, sick));

            assertThat(result.approved()).containsExactly(unpaid, sick);
            assertThat(result.skipped()).containsExactly(new Skipped(annual, "Không đủ phép năm"));
            assertThat(jdbc.queryForObject("select current_step from tk_requests where id = ?", Integer.class, unpaid))
                    .isEqualTo(2);
        } finally {
            transactions.executeWithoutResult(status -> Fixture.purge(jdbc, t));
        }
    }

    @Test
    void bulkSizeIsCapped() {
        assertThatThrownBy(() -> inbox.bulkApprove(principal("tk-cap", "lead", "timekeeping.approve"),
                java.util.Collections.nCopies(51, "x")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private String submit(String t, Fixture f, LeaveKind kind, LocalDate from, LocalDate to) {
        RequestView draft = requests.create(t, f.teacher(),
                new RequestCommand(RequestType.LEAVE, kind, from, to, DayPart.FULL, null, null, null, "Việc riêng"));
        return requests.submit(t, f.teacher(), draft.id()).id();
    }
}

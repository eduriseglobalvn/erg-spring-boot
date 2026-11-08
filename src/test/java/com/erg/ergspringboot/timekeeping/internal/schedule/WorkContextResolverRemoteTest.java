package com.erg.ergspringboot.timekeeping.internal.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.timekeeping.RemoteRuleKind;
import com.erg.ergspringboot.timekeeping.persistence.TkPeriodLockRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkPolicyRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRemoteRule;
import com.erg.ergspringboot.timekeeping.persistence.TkRemoteRuleRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShiftRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkSiteRepository;
import com.erg.ergspringboot.users.persistence.organization.UserAccessScopeRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** An approved WFH request makes the day's punches remote, with or without a remote rule (ruling 6). */
class WorkContextResolverRemoteTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);

    private final TkRemoteRuleRepository rules = mock(TkRemoteRuleRepository.class);
    private final TkRequestRepository requests = mock(TkRequestRepository.class);
    private final WorkContextResolver resolver = new WorkContextResolver(mock(UserAccessScopeRepository.class),
            mock(TkShiftRepository.class), mock(TkSiteRepository.class), mock(TkPolicyRepository.class), rules,
            mock(TkPeriodLockRepository.class), requests);

    @Test
    void anApprovedWfhRequestIsRemoteWithoutAnyRule() {
        when(rules.findByTenantIdAndSchoolIdAndActiveTrue("t", "s")).thenReturn(List.of());
        when(requests.hasApprovedWfh("t", "u", DAY)).thenReturn(true);

        assertThat(resolver.isRemote("t", "s", "u", DAY, "shift")).isTrue();
    }

    @Test
    void anApprovedWfhRuleAloneDoesNotMakeEveryoneRemote() {
        TkRemoteRule rule = new TkRemoteRule();
        rule.setKind(RemoteRuleKind.APPROVED_WFH);
        when(rules.findByTenantIdAndSchoolIdAndActiveTrue("t", "s")).thenReturn(List.of(rule));
        when(requests.hasApprovedWfh("t", "u", DAY)).thenReturn(false);

        assertThat(resolver.isRemote("t", "s", "u", DAY, "shift")).isFalse();
    }
}

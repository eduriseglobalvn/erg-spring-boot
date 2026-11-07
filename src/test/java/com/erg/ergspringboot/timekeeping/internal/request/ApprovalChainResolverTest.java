package com.erg.ergspringboot.timekeeping.internal.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.timekeeping.RequestType;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.ApproverRef;
import com.erg.ergspringboot.timekeeping.internal.request.ApprovalChainResolver.ChainStep;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkPolicy;
import com.erg.ergspringboot.users.persistence.EmployeeProfile;
import com.erg.ergspringboot.users.persistence.EmployeeProfileRepository;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.organization.UserAccessScope;
import com.erg.ergspringboot.users.persistence.organization.UserAccessScopeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApprovalChainResolverTest {

    private static final String T = "tenant-a";
    private static final String SCHOOL = "school-1";
    private static final String REQUESTER = "teacher-1";

    private final EmployeeProfileRepository profiles = mock(EmployeeProfileRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserAccessScopeRepository scopes = mock(UserAccessScopeRepository.class);
    private final WorkContextResolver context = mock(WorkContextResolver.class);
    private final TkPolicy policy = new TkPolicy();
    private final List<UserAccessScope> scopeRows = new ArrayList<>();
    private final ApprovalChainResolver resolver = new ApprovalChainResolver(profiles, users, scopes, context,
            Clock.fixed(Instant.parse("2026-11-12T01:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void wire() {
        when(context.schoolOf(T, REQUESTER)).thenReturn(Optional.of(SCHOOL));
        when(context.policyFor(eq(T), eq(SCHOOL), any())).thenReturn(policy);
        manager("lead-1");
        when(users.findActiveUserIdsHoldingRole(T, "school.vice_principal")).thenReturn(List.of("vp-1"));
        when(users.findActiveUserIdsHoldingPermission(eq(T), eq("timekeeping.approve"), any()))
                .thenReturn(List.of("approver-other-school", "approver-1"));
        when(users.findActiveUserIdsHoldingPermission(eq(T), eq("timekeeping.manage"), any()))
                .thenReturn(List.of("admin-1"));
        inSchool("lead-1", SCHOOL);
        inSchool("vp-1", SCHOOL);
        inSchool("approver-1", SCHOOL);
        inSchool("approver-other-school", "school-2");
        when(scopes.findByUserIdInOrderByCreatedAtAsc(anyCollection())).thenAnswer(call -> {
            Collection<?> ids = call.getArgument(0);
            return scopeRows.stream().filter(s -> ids.contains(s.getUserId())).toList();
        });
        when(users.findByTenantIdAndIdIn(eq(T), anyCollection())).thenAnswer(call -> active(call.getArgument(1)));
        when(users.findByTenantIdAndIdInAnyStatus(eq(T), anyCollection())).thenAnswer(call -> active(call.getArgument(1)));
    }

    @Test
    void twoDaysOfLeaveGoToTheManagerThenTheVicePrincipal() {
        List<ChainStep> steps = resolver.resolve(T, SCHOOL, REQUESTER, RequestType.LEAVE, new BigDecimal("2"));

        assertThat(steps).containsExactly(
                new ChainStep(1, "MANAGER", List.of("lead-1"), false),
                new ChainStep(2, "SCHOOL_ROLE:VICE_PRINCIPAL", List.of("vp-1"), false));
    }

    @Test
    void halfADayIsOneStep() {
        assertThat(resolver.resolve(T, SCHOOL, REQUESTER, RequestType.LEAVE, new BigDecimal("0.5")))
                .containsExactly(new ChainStep(1, "MANAGER", List.of("lead-1"), false));
        assertThat(resolver.resolve(T, SCHOOL, REQUESTER, RequestType.EXPLANATION, BigDecimal.ZERO)).hasSize(1);
        assertThat(resolver.resolve(T, SCHOOL, REQUESTER, RequestType.BUSINESS, BigDecimal.ONE)).hasSize(2);
    }

    @Test
    void noManagerFallsBackToTheSchoolsApproveHolders() {
        manager(null);

        assertThat(resolver.resolve(T, SCHOOL, REQUESTER, RequestType.WFH, BigDecimal.ONE))
                .containsExactly(new ChainStep(1, "MANAGER", List.of("approver-1"), false));
    }

    @Test
    void anInactiveManagerCountsAsNoManager() {
        manager("gone-1");
        inSchool("gone-1", SCHOOL);

        assertThat(resolver.resolve(T, SCHOOL, REQUESTER, RequestType.WFH, BigDecimal.ONE).get(0).approverIds())
                .containsExactly("approver-1");
    }

    @Test
    void aStepWhoseApproverIsTheRequesterIsSkipped() {
        manager(REQUESTER);

        assertThat(resolver.resolve(T, SCHOOL, REQUESTER, RequestType.LEAVE, new BigDecimal("2"))).containsExactly(
                new ChainStep(1, "MANAGER", List.of(), true),
                new ChainStep(2, "SCHOOL_ROLE:VICE_PRINCIPAL", List.of("vp-1"), false));
    }

    @Test
    void whenEveryStepIsSkippedItEscalatesToAnotherApproveHolderThenToManageHolders() {
        manager(REQUESTER);

        assertThat(resolver.resolve(T, SCHOOL, REQUESTER, RequestType.WFH, BigDecimal.ONE)).containsExactly(
                new ChainStep(1, "MANAGER", List.of(), true),
                new ChainStep(2, "ESCALATION", List.of("approver-1"), false));

        when(users.findActiveUserIdsHoldingPermission(eq(T), eq("timekeeping.approve"), any())).thenReturn(List.of(REQUESTER));
        inSchool(REQUESTER, SCHOOL);
        assertThat(resolver.resolve(T, SCHOOL, REQUESTER, RequestType.WFH, BigDecimal.ONE).get(1))
                .isEqualTo(new ChainStep(2, "ESCALATION", List.of("admin-1"), false));

        when(users.findActiveUserIdsHoldingPermission(eq(T), eq("timekeeping.manage"), any())).thenReturn(List.of(REQUESTER));
        assertThat(resolver.resolve(T, SCHOOL, REQUESTER, RequestType.WFH, BigDecimal.ONE))
                .containsExactly(new ChainStep(1, "MANAGER", List.of(), true));
    }

    @Test
    void severalRoleHoldersAllHoldTheStepAndAMissingRoleFallsBack() {
        when(users.findActiveUserIdsHoldingRole(T, "school.vice_principal")).thenReturn(List.of("vp-1", "vp-2", "vp-elsewhere"));
        inSchool("vp-2", SCHOOL);
        inSchool("vp-elsewhere", "school-2");
        assertThat(resolver.resolve(T, SCHOOL, REQUESTER, RequestType.BUSINESS, BigDecimal.ONE).get(1).approverIds())
                .containsExactly("vp-1", "vp-2");

        when(users.findActiveUserIdsHoldingRole(T, "school.vice_principal")).thenReturn(List.of());
        assertThat(resolver.resolve(T, SCHOOL, REQUESTER, RequestType.BUSINESS, BigDecimal.ONE).get(1).approverIds())
                .containsExactly("approver-1");
    }

    @Test
    void thePolicyChainPicksTheGreatestMinDaysNotAboveTheRequestedDays() {
        policy.setApprovalChains("""
                {"LEAVE":[{"minDays":0,"steps":["MANAGER"]},{"minDays":3,"steps":["MANAGER","SCHOOL_ROLE:VICE_PRINCIPAL"]}],
                 "WFH":[{"minDays":0,"steps":["SCHOOL_ROLE:VICE_PRINCIPAL"]}]}""");

        assertThat(resolver.resolve(T, SCHOOL, REQUESTER, RequestType.LEAVE, new BigDecimal("2"))).hasSize(1);
        assertThat(resolver.resolve(T, SCHOOL, REQUESTER, RequestType.LEAVE, new BigDecimal("3"))).hasSize(2);
        assertThat(resolver.resolve(T, SCHOOL, REQUESTER, RequestType.WFH, BigDecimal.ONE))
                .containsExactly(new ChainStep(1, "SCHOOL_ROLE:VICE_PRINCIPAL", List.of("vp-1"), false));
        // no BUSINESS entry -> built-in default
        assertThat(resolver.resolve(T, SCHOOL, REQUESTER, RequestType.BUSINESS, BigDecimal.ONE)).hasSize(2);

        policy.setApprovalChains("not json");
        assertThat(resolver.resolve(T, SCHOOL, REQUESTER, RequestType.LEAVE, new BigDecimal("2"))).hasSize(2);
    }

    @Test
    void resolveChainNamesEachApprover() {
        assertThat(resolver.resolveChain(T, REQUESTER, RequestType.LEAVE, new BigDecimal("2"))).containsExactly(
                new ApproverRef(1, "MANAGER", "lead-1", "Name lead-1", "Quản lý trực tiếp"),
                new ApproverRef(2, "SCHOOL_ROLE:VICE_PRINCIPAL", "vp-1", "Name vp-1", "Phó hiệu trưởng"));
    }

    private void manager(String managerId) {
        EmployeeProfile profile = new EmployeeProfile();
        profile.setManagerId(managerId);
        when(profiles.findByUserIdAndTenantId(REQUESTER, T)).thenReturn(Optional.of(profile));
    }

    private void inSchool(String userId, String schoolId) {
        UserAccessScope scope = new UserAccessScope();
        scope.setUserId(userId);
        scope.setEducationUnitId(schoolId);
        scopeRows.add(scope);
    }

    private static List<User> active(Collection<?> ids) {
        return ids.stream().map(Object::toString).filter(id -> !id.startsWith("gone")).map(id -> {
            User user = new User();
            user.setId(id);
            user.setFullName("Name " + id);
            return user;
        }).toList();
    }
}

package com.erg.ergspringboot.notifications.internal.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.notifications.CampaignAudience;
import com.erg.ergspringboot.notifications.CampaignStatus;
import com.erg.ergspringboot.notifications.NotificationCampaignRequests.CampaignDraftRequest;
import com.erg.ergspringboot.notifications.NotificationPriority;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignRepository;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.users.UserAccountProjection;
import com.erg.ergspringboot.users.UserAuthorizationPort;
import com.erg.ergspringboot.users.UserAuthorizationProjection;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class NotificationCampaignServiceTest {

    private static final String TENANT = "tenant_a";
    private static final Instant NOW = Instant.parse("2026-11-07T03:00:00Z");
    private static final CampaignActor ADMIN = new CampaignActor(TENANT, "admin-1", Set.of("admin"));
    private static final CampaignActor TEACHER = new CampaignActor(TENANT, "teacher-1", Set.of("teacher"));
    private static final CampaignAudience CLASS_7A = audience(List.of("class-7a"), List.of());

    @Mock private NotificationCampaignRepository campaigns;
    @Mock private AudienceScopeGuard guard;
    @Mock private NotificationAudienceResolver resolver;
    @Mock private NotificationCampaignQueryService queries;
    @Mock private UserAuthorizationPort authorizations;
    @Mock private UserDirectoryPort users;
    @Mock private CampaignFanOut fanOut;

    private NotificationCampaignService service;

    @BeforeEach
    void setUp() {
        service = new NotificationCampaignService(campaigns, guard, resolver, fanOut, queries, authorizations, users,
                new ErgPolicyService(), Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(guard.isAdmin(any(CampaignActor.class)))
                .thenAnswer(invocation -> invocation.<CampaignActor>getArgument(0).roles().contains("admin"));
        lenient().when(campaigns.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sendMarksSendingAndTriggersTheFanOutWithoutWritingAnyRecipient() {
        NotificationCampaignEntity campaign = campaign("c1", "admin-1", CampaignStatus.DRAFT, NotificationPriority.INFO);
        when(campaigns.lockByTenantIdAndId(TENANT, "c1")).thenReturn(Optional.of(campaign));
        when(resolver.resolve(TENANT, CLASS_7A)).thenReturn(resolved(people(32)));

        service.send(ADMIN, "c1");

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.SENDING);
        assertThat(campaign.getResolvedCount()).isEqualTo(32);
        InOrder order = inOrder(guard, resolver, campaigns, fanOut);
        order.verify(guard).assertAllowed(ADMIN, CLASS_7A);
        order.verify(resolver).resolve(TENANT, CLASS_7A);
        order.verify(campaigns).save(campaign);
        order.verify(fanOut).triggerAfterCommit(TENANT, "c1");
        verify(fanOut, never()).run(any(), any());
    }

    @Test
    void aSecondSendOnASendingOrSentCampaignReturnsItWithoutDoingAnything() {
        for (CampaignStatus status : List.of(CampaignStatus.SENDING, CampaignStatus.SENT)) {
            when(campaigns.lockByTenantIdAndId(TENANT, "c1"))
                    .thenReturn(Optional.of(campaign("c1", "admin-1", status, NotificationPriority.INFO)));

            service.send(ADMIN, "c1");
        }

        verify(queries, org.mockito.Mockito.times(2)).toResponse(any());
        verifyNoInteractions(resolver, fanOut);
        verify(guard, never()).assertAllowed(any(CampaignActor.class), any());
        verify(campaigns, never()).save(any());
    }

    @Test
    void sendOnACancelledOrFailedCampaignIsAConflictAndWritesNothing() {
        for (CampaignStatus status : List.of(CampaignStatus.CANCELLED, CampaignStatus.FAILED)) {
            when(campaigns.lockByTenantIdAndId(TENANT, "c1"))
                    .thenReturn(Optional.of(campaign("c1", "admin-1", status, NotificationPriority.INFO)));

            assertThatThrownBy(() -> service.send(ADMIN, "c1")).isInstanceOf(ConflictException.class);
        }
        verifyNoInteractions(resolver, fanOut);
        verify(campaigns, never()).save(any());
    }

    @Test
    void sendThatResolvesNobodyIsRejected() {
        when(campaigns.lockByTenantIdAndId(TENANT, "c1"))
                .thenReturn(Optional.of(campaign("c1", "admin-1", CampaignStatus.DRAFT, NotificationPriority.INFO)));
        when(resolver.resolve(TENANT, CLASS_7A)).thenReturn(resolved(List.of()));

        assertThatThrownBy(() -> service.send(ADMIN, "c1")).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(fanOut);
        verify(campaigns, never()).save(any());
    }

    @Test
    void scheduleRejectsPastAndBeyondThirtyDays() {
        assertThatThrownBy(() -> service.schedule(ADMIN, "c1", at(NOW.minusSeconds(60))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Thời điểm hẹn phải ở tương lai");
        assertThatThrownBy(() -> service.schedule(ADMIN, "c1", at(NOW)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Thời điểm hẹn phải ở tương lai");
        assertThatThrownBy(() -> service.schedule(ADMIN, "c1", at(NOW.plusSeconds(31L * 24 * 3600))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Chỉ hẹn tối đa 30 ngày tới");
        verifyNoInteractions(campaigns, guard);
    }

    @Test
    void scheduleWithinThirtyDaysChecksScopeThenSchedules() {
        NotificationCampaignEntity campaign = campaign("c1", "admin-1", CampaignStatus.DRAFT, NotificationPriority.INFO);
        when(campaigns.lockByTenantIdAndId(TENANT, "c1")).thenReturn(Optional.of(campaign));
        OffsetDateTime when = at(NOW.plusSeconds(30L * 24 * 3600));

        service.schedule(ADMIN, "c1", when);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);
        assertThat(campaign.getScheduledAt()).isEqualTo(when);
        InOrder order = inOrder(guard, campaigns);
        order.verify(guard).assertAllowed(ADMIN, CLASS_7A);
        order.verify(campaigns).save(campaign);
    }

    @Test
    void cancelOnlyAppliesToScheduledCampaignsNotOnesAlreadySending() {
        for (CampaignStatus status : List.of(CampaignStatus.SENT, CampaignStatus.SENDING)) {
            when(campaigns.lockByTenantIdAndId(TENANT, "c1"))
                    .thenReturn(Optional.of(campaign("c1", "admin-1", status, NotificationPriority.INFO)));

            assertThatThrownBy(() -> service.cancel(ADMIN, "c1")).isInstanceOf(ConflictException.class);
        }
        verify(campaigns, never()).save(any());
    }

    @Test
    void createChecksScopeBeforeWritingAndNormalisesRoles() {
        CampaignAudience lowerCase = new CampaignAudience(
                false, List.of(), List.of(), List.of(), List.of(), List.of(" student "), List.of());
        CampaignAudience normalised = new CampaignAudience(
                false, List.of(), List.of(), List.of(), List.of(), List.of("STUDENT"), List.of());
        when(resolver.resolve(TENANT, normalised)).thenReturn(resolved(people(5)));

        service.create(ADMIN, draft(lowerCase, "urgent"));

        ArgumentCaptor<NotificationCampaignEntity> saved = ArgumentCaptor.forClass(NotificationCampaignEntity.class);
        InOrder order = inOrder(guard, campaigns);
        order.verify(guard).assertAllowed(ADMIN, normalised);
        order.verify(campaigns).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(saved.getValue().getPriority()).isEqualTo(NotificationPriority.URGENT);
        assertThat(saved.getValue().getSenderRole()).isEqualTo("ADMIN");
        assertThat(saved.getValue().getEstimatedCount()).isEqualTo(5);
        assertThat(saved.getValue().isPushEnabled()).isTrue();
    }

    @Test
    void createStopsWhenTheGuardDenies() {
        doThrow(new AccessDeniedException("no")).when(guard).assertAllowed(any(CampaignActor.class), any());

        assertThatThrownBy(() -> service.create(TEACHER, draft(CLASS_7A, null)))
                .isInstanceOf(AccessDeniedException.class);
        verify(campaigns, never()).save(any());
        verifyNoInteractions(resolver);
    }

    @Test
    void requestValidationRejectsBadInput() {
        CampaignAudience empty = new CampaignAudience(false, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.estimate(ADMIN, empty))
                .isInstanceOf(BadRequestException.class).hasMessage("Chọn ít nhất một người nhận");
        CampaignAudience parents = new CampaignAudience(false, null, null, null, null, List.of("PARENT"), null);
        assertThatThrownBy(() -> service.estimate(ADMIN, parents)).isInstanceOf(BadRequestException.class);
        CampaignAudience tooMany = audience(people(5_001), List.of());
        assertThatThrownBy(() -> service.estimate(ADMIN, tooMany)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(ADMIN, draft(CLASS_7A, "normal")))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(guard, resolver, campaigns);
    }

    @Test
    void anUpdateOfASendingCampaignIsRefused() {
        when(campaigns.lockByTenantIdAndId(TENANT, "c1"))
                .thenReturn(Optional.of(campaign("c1", "admin-1", CampaignStatus.SENDING, NotificationPriority.INFO)));

        assertThatThrownBy(() -> service.update(ADMIN, "c1", draft(CLASS_7A, null))).isInstanceOf(ConflictException.class);
        verify(campaigns, never()).save(any());
    }

    @Test
    void estimateChecksScopeBeforeResolving() {
        when(resolver.resolve(TENANT, CLASS_7A)).thenReturn(resolved(people(4)));

        assertThat(service.estimate(TEACHER, CLASS_7A).total()).isEqualTo(4);

        InOrder order = inOrder(guard, resolver);
        order.verify(guard).assertAllowed(TEACHER, CLASS_7A);
        order.verify(resolver).resolve(TENANT, CLASS_7A);
    }

    @Test
    void teacherCannotTouchAnotherTeachersCampaign() {
        when(campaigns.lockByTenantIdAndId(TENANT, "c1"))
                .thenReturn(Optional.of(campaign("c1", "teacher-2", CampaignStatus.DRAFT, NotificationPriority.INFO)));

        assertThatThrownBy(() -> service.send(TEACHER, "c1")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.cancel(TEACHER, "c1")).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(resolver);
    }

    @Test
    void scheduledDispatchSkipsWhenTheConditionalClaimLoses() {
        when(campaigns.claimDue(eq(TENANT), eq("c1"), any())).thenReturn(0);

        assertThat(service.dispatchDue(TENANT, "c1")).isFalse();

        verifyNoInteractions(resolver, guard, fanOut);
    }

    @Test
    void scheduledDispatchRevalidatesTheCreatorAndLeavesTheCampaignSendingForTheFanOut() {
        NotificationCampaignEntity campaign = campaign("c1", "teacher-1", CampaignStatus.SENDING, NotificationPriority.INFO);
        campaign.setEstimatedCount(31);
        when(campaigns.claimDue(eq(TENANT), eq("c1"), any())).thenReturn(1);
        when(campaigns.findByTenantIdAndId(TENANT, "c1")).thenReturn(Optional.of(campaign));
        when(users.findByTenantIdAndIds(TENANT, List.of("teacher-1")))
                .thenReturn(List.of(new UserAccountProjection("teacher-1", TENANT, "GV", null, "ACTIVE")));
        when(authorizations.findEffective("teacher-1", TENANT)).thenReturn(Optional.of(new UserAuthorizationProjection(
                "teacher-1", TENANT, List.of("lms_teacher_standard"), List.of("lms.assignment.read", "notification.campaign.send"),
                List.of(), List.of())));

        assertThat(service.dispatchDue(TENANT, "c1")).isTrue();

        verify(guard).assertAllowed(new CampaignActor(TENANT, "teacher-1", Set.of("lms_teacher_standard"),
                List.of("lms.assignment.read", "notification.campaign.send"), List.of()), CLASS_7A);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.SENDING);
        verifyNoInteractions(resolver);
    }

    @Test
    void scheduledDispatchFailsTheCampaignWhenTheCreatorLostScope() {
        NotificationCampaignEntity campaign = campaign("c1", "teacher-1", CampaignStatus.SENDING, NotificationPriority.INFO);
        when(campaigns.claimDue(eq(TENANT), eq("c1"), any())).thenReturn(1);
        when(campaigns.findByTenantIdAndId(TENANT, "c1")).thenReturn(Optional.of(campaign));
        when(users.findByTenantIdAndIds(TENANT, List.of("teacher-1")))
                .thenReturn(List.of(new UserAccountProjection("teacher-1", TENANT, "GV", null, "SUSPENDED")));
        doThrow(new AccessDeniedException("no")).when(guard)
                .assertAllowed(CampaignActor.none(TENANT, "teacher-1"), CLASS_7A);

        assertThat(service.dispatchDue(TENANT, "c1")).isFalse();

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.FAILED);
        verifyNoInteractions(resolver);
    }

    @Test
    void scheduledDispatchFailsTheCampaignWhenTheCreatorNoLongerHoldsTheSendPermission() {
        NotificationCampaignEntity campaign = campaign("c1", "teacher-1", CampaignStatus.SENDING, NotificationPriority.INFO);
        when(campaigns.claimDue(eq(TENANT), eq("c1"), any())).thenReturn(1);
        when(campaigns.findByTenantIdAndId(TENANT, "c1")).thenReturn(Optional.of(campaign));
        when(users.findByTenantIdAndIds(TENANT, List.of("teacher-1")))
                .thenReturn(List.of(new UserAccountProjection("teacher-1", TENANT, "GV", null, "ACTIVE")));
        when(authorizations.findEffective("teacher-1", TENANT)).thenReturn(Optional.of(new UserAuthorizationProjection(
                "teacher-1", TENANT, List.of("teacher"), List.of("lms.assignment.read"), List.of(), List.of())));
        doThrow(new AccessDeniedException("no")).when(guard)
                .assertAllowed(CampaignActor.none(TENANT, "teacher-1"), CLASS_7A);

        assertThat(service.dispatchDue(TENANT, "c1")).isFalse();

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.FAILED);
        verifyNoInteractions(resolver);
    }

    @Test
    void scheduledDispatchAcceptsAWildcardSendGrantLikeTheHttpGate() {
        NotificationCampaignEntity campaign = dueCampaignOf("teacher-1");
        creatorHolds(List.of("notification.*", "lms.assignment.read"), List.of());
        // A creator wrongly reduced to "nobody" would be denied here and end FAILED.
        lenient().doThrow(new AccessDeniedException("no")).when(guard)
                .assertAllowed(CampaignActor.none(TENANT, "teacher-1"), CLASS_7A);

        assertThat(service.dispatchDue(TENANT, "c1")).isTrue();

        verify(guard).assertAllowed(new CampaignActor(TENANT, "teacher-1", Set.of("lms_teacher_standard"),
                List.of("notification.*", "lms.assignment.read"), List.of()), CLASS_7A);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.SENDING);
    }

    @Test
    void scheduledDispatchHonoursWildcardDeniesLikeTheHttpGate() {
        for (String deny : List.of("notification.*", "*.send", "*", " Notification.* ", "NOTIFICATION.CAMPAIGN.SEND")) {
            org.mockito.Mockito.reset(campaigns, guard, users, authorizations);
            NotificationCampaignEntity campaign = dueCampaignOf("teacher-1");
            creatorHolds(List.of("notification.campaign.send", "lms.assignment.read"), List.of(deny));
            doThrow(new AccessDeniedException("no")).when(guard)
                    .assertAllowed(CampaignActor.none(TENANT, "teacher-1"), CLASS_7A);

            assertThat(service.dispatchDue(TENANT, "c1")).as(deny).isFalse();

            assertThat(campaign.getStatus()).as(deny).isEqualTo(CampaignStatus.FAILED);
        }
        verifyNoInteractions(resolver);
    }

    private NotificationCampaignEntity dueCampaignOf(String creator) {
        NotificationCampaignEntity campaign = campaign("c1", creator, CampaignStatus.SENDING, NotificationPriority.INFO);
        when(campaigns.claimDue(eq(TENANT), eq("c1"), any())).thenReturn(1);
        when(campaigns.findByTenantIdAndId(TENANT, "c1")).thenReturn(Optional.of(campaign));
        lenient().when(campaigns.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return campaign;
    }

    private void creatorHolds(List<String> rolePermissions, List<String> denied) {
        when(users.findByTenantIdAndIds(TENANT, List.of("teacher-1")))
                .thenReturn(List.of(new UserAccountProjection("teacher-1", TENANT, "GV", null, "ACTIVE")));
        when(authorizations.findEffective("teacher-1", TENANT)).thenReturn(Optional.of(new UserAuthorizationProjection(
                "teacher-1", TENANT, List.of("lms_teacher_standard"), rolePermissions, List.of(), denied)));
    }

    private static NotificationCampaignEntity campaign(
            String id, String createdBy, CampaignStatus status, NotificationPriority priority) {
        NotificationCampaignEntity campaign = new NotificationCampaignEntity();
        campaign.setId(id);
        campaign.setTenantId(TENANT);
        campaign.setCreatedBy(createdBy);
        campaign.setSenderRole("ADMIN");
        campaign.setTitle("Họp phụ huynh");
        campaign.setBody("Nội dung");
        campaign.setPriority(priority);
        campaign.setAudience(CLASS_7A);
        campaign.setStatus(status);
        return campaign;
    }

    private static CampaignDraftRequest draft(CampaignAudience audience, String priority) {
        return new CampaignDraftRequest("Họp phụ huynh", "Nội dung", priority, audience, false, null);
    }

    private static CampaignAudience audience(List<String> classIds, List<String> userIds) {
        return new CampaignAudience(false, List.of(), List.of(), classIds, List.of(), List.of(), userIds);
    }

    private static ResolvedAudience resolved(List<String> userIds) {
        return new ResolvedAudience(userIds, new AudienceBreakdown(userIds.size(), 0, userIds.size(), 0, 0, 0));
    }

    private static List<String> people(int count) {
        return IntStream.range(0, count).mapToObj(index -> "u" + index).toList();
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}

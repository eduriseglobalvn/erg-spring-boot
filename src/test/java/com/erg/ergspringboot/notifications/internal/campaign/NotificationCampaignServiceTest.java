package com.erg.ergspringboot.notifications.internal.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.erg.ergspringboot.notifications.NotificationTokenCipher;
import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.notifications.internal.NotificationCommandService;
import com.erg.ergspringboot.notifications.internal.NotificationMetrics;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationMessageEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationMessageRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationOutboxEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationOutboxRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationPreferenceEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationPreferenceRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationRecipientRepository;
import com.erg.ergspringboot.users.UserAccountProjection;
import com.erg.ergspringboot.users.UserAuthorizationPort;
import com.erg.ergspringboot.users.UserAuthorizationProjection;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
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
    @Mock private NotificationMessageRepository messages;
    @Mock private NotificationRecipientRepository recipients;
    @Mock private NotificationDeviceRegistrationRepository devices;
    @Mock private NotificationOutboxRepository outbox;
    @Mock private NotificationPreferenceRepository preferences;
    @Mock private NotificationMetrics metrics;

    private NotificationCampaignService service;

    @BeforeEach
    void setUp() {
        NotificationCommandService commands = new NotificationCommandService(messages, recipients, devices, outbox,
                new NotificationPushProperties(), new NotificationTokenCipher(new NotificationPushProperties()),
                metrics, preferences);
        service = new NotificationCampaignService(campaigns, guard, resolver, commands, queries, authorizations, users,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(guard.isAdmin(Set.of("admin"))).thenReturn(true);
        lenient().when(campaigns.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(messages.saveAll(any())).thenAnswer(invocation -> {
            Collection<NotificationMessageEntity> saved = invocation.getArgument(0);
            int[] next = {0};
            saved.forEach(message -> message.setId("msg-" + next[0]++));
            return new ArrayList<>(saved);
        });
    }

    @Test
    void sendOnDraftCreatesOneMessagePerRecipientWithPushOutboxAndMarksSent() {
        NotificationCampaignEntity campaign = campaign("c1", "admin-1", CampaignStatus.DRAFT, NotificationPriority.INFO);
        when(campaigns.lockByTenantIdAndId(TENANT, "c1")).thenReturn(Optional.of(campaign));
        List<String> people = people(32);
        when(resolver.resolve(TENANT, CLASS_7A)).thenReturn(resolved(people));
        when(devices.findByTenantIdAndUserIdInAndPortalAndEnabledTrue(eq(TENANT), any(), eq("lms")))
                .thenReturn(people.stream().map(NotificationCampaignServiceTest::device).toList());

        service.send(ADMIN, "c1");

        List<NotificationMessageEntity> sentMessages = captureMessages();
        assertThat(sentMessages).hasSize(32).allSatisfy(message -> {
            assertThat(message.getCampaignId()).isEqualTo("c1");
            assertThat(message.getPriority()).isEqualTo("INFO");
            assertThat(message.getTitle()).isEqualTo("Họp phụ huynh");
        });
        assertThat(captureOutbox()).hasSize(32)
                .allSatisfy(entry -> assertThat(entry.getPayload()).doesNotContainKey("bypassPreferences"));
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.SENT);
        assertThat(campaign.getResolvedCount()).isEqualTo(32);
        assertThat(campaign.getSentAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        InOrder order = inOrder(guard, resolver, messages, campaigns);
        order.verify(guard).assertAllowed(TENANT, "admin-1", Set.of("admin"), CLASS_7A);
        order.verify(resolver).resolve(TENANT, CLASS_7A);
        order.verify(messages).saveAll(any());
        order.verify(campaigns).save(campaign);
    }

    @Test
    void sendWithoutPushWritesInboxOnly() {
        NotificationCampaignEntity campaign = campaign("c1", "admin-1", CampaignStatus.DRAFT, NotificationPriority.INFO);
        campaign.setPushEnabled(false);
        when(campaigns.lockByTenantIdAndId(TENANT, "c1")).thenReturn(Optional.of(campaign));
        when(resolver.resolve(TENANT, CLASS_7A)).thenReturn(resolved(people(3)));

        service.send(ADMIN, "c1");

        assertThat(captureMessages()).hasSize(3);
        verifyNoInteractions(devices, outbox);
    }

    @Test
    void sendOnSentCampaignIsAConflictAndWritesNothing() {
        when(campaigns.lockByTenantIdAndId(TENANT, "c1"))
                .thenReturn(Optional.of(campaign("c1", "admin-1", CampaignStatus.SENT, NotificationPriority.INFO)));

        assertThatThrownBy(() -> service.send(ADMIN, "c1")).isInstanceOf(ConflictException.class);
        verifyNoInteractions(resolver, messages);
        verify(campaigns, never()).save(any());
    }

    @Test
    void sendThatResolvesNobodyIsRejected() {
        when(campaigns.lockByTenantIdAndId(TENANT, "c1"))
                .thenReturn(Optional.of(campaign("c1", "admin-1", CampaignStatus.DRAFT, NotificationPriority.INFO)));
        when(resolver.resolve(TENANT, CLASS_7A)).thenReturn(resolved(List.of()));

        assertThatThrownBy(() -> service.send(ADMIN, "c1")).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(messages);
    }

    @Test
    void urgentPushIgnoresOptOutAndFlagsThePayload() {
        NotificationCampaignEntity campaign = campaign("c1", "admin-1", CampaignStatus.DRAFT, NotificationPriority.URGENT);
        when(campaigns.lockByTenantIdAndId(TENANT, "c1")).thenReturn(Optional.of(campaign));
        when(resolver.resolve(TENANT, CLASS_7A)).thenReturn(resolved(List.of("u0", "u1")));
        when(devices.findByTenantIdAndUserIdInAndPortalAndEnabledTrue(eq(TENANT), any(), eq("lms")))
                .thenReturn(List.of(device("u0"), device("u1")));

        service.send(ADMIN, "c1");

        assertThat(captureOutbox()).hasSize(2)
                .allSatisfy(entry -> assertThat(entry.getPayload()).containsEntry("bypassPreferences", "true"));
        verifyNoInteractions(preferences);
    }

    @Test
    void nonUrgentPushHonoursThePushOptOut() {
        NotificationCampaignEntity campaign = campaign("c1", "admin-1", CampaignStatus.DRAFT, NotificationPriority.IMPORTANT);
        when(campaigns.lockByTenantIdAndId(TENANT, "c1")).thenReturn(Optional.of(campaign));
        when(resolver.resolve(TENANT, CLASS_7A)).thenReturn(resolved(List.of("u0", "u1")));
        NotificationPreferenceEntity optOut = new NotificationPreferenceEntity();
        optOut.setUserId("u1");
        optOut.setPushEnabled(false);
        when(preferences.findByTenantIdAndPortalAndEventTypeAndUserIdIn(eq(TENANT), eq("lms"), eq("CAMPAIGN"), any()))
                .thenReturn(List.of(optOut));
        when(devices.findByTenantIdAndUserIdInAndPortalAndEnabledTrue(eq(TENANT), any(), eq("lms")))
                .thenReturn(List.of(device("u0"), device("u1")));

        service.send(ADMIN, "c1");

        assertThat(captureMessages()).hasSize(2);
        assertThat(captureOutbox()).singleElement()
                .satisfies(entry -> assertThat(entry.getPayload()).containsEntry("deviceRegistrationId", "dev-u0"));
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
        order.verify(guard).assertAllowed(TENANT, "admin-1", Set.of("admin"), CLASS_7A);
        order.verify(campaigns).save(campaign);
    }

    @Test
    void cancelOnlyAppliesToScheduledCampaigns() {
        when(campaigns.lockByTenantIdAndId(TENANT, "c1"))
                .thenReturn(Optional.of(campaign("c1", "admin-1", CampaignStatus.SENT, NotificationPriority.INFO)));

        assertThatThrownBy(() -> service.cancel(ADMIN, "c1")).isInstanceOf(ConflictException.class);
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
        order.verify(guard).assertAllowed(TENANT, "admin-1", Set.of("admin"), normalised);
        order.verify(campaigns).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(saved.getValue().getPriority()).isEqualTo(NotificationPriority.URGENT);
        assertThat(saved.getValue().getSenderRole()).isEqualTo("ADMIN");
        assertThat(saved.getValue().getEstimatedCount()).isEqualTo(5);
        assertThat(saved.getValue().isPushEnabled()).isTrue();
    }

    @Test
    void createStopsWhenTheGuardDenies() {
        doThrow(new AccessDeniedException("no")).when(guard).assertAllowed(anyString(), anyString(), any(), any());

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
    void estimateChecksScopeBeforeResolving() {
        when(resolver.resolve(TENANT, CLASS_7A)).thenReturn(resolved(people(4)));

        assertThat(service.estimate(TEACHER, CLASS_7A).total()).isEqualTo(4);

        InOrder order = inOrder(guard, resolver);
        order.verify(guard).assertAllowed(TENANT, "teacher-1", Set.of("teacher"), CLASS_7A);
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

        verifyNoInteractions(resolver, guard, messages);
    }

    @Test
    void scheduledDispatchRevalidatesTheCreatorAndResolvesRecipientsAgain() {
        NotificationCampaignEntity campaign = campaign("c1", "teacher-1", CampaignStatus.SENDING, NotificationPriority.INFO);
        campaign.setEstimatedCount(31);
        when(campaigns.claimDue(eq(TENANT), eq("c1"), any())).thenReturn(1);
        when(campaigns.findByTenantIdAndId(TENANT, "c1")).thenReturn(Optional.of(campaign));
        when(users.findByTenantIdAndIds(TENANT, List.of("teacher-1")))
                .thenReturn(List.of(new UserAccountProjection("teacher-1", TENANT, "GV", null, "ACTIVE")));
        when(authorizations.find("teacher-1", TENANT)).thenReturn(Optional.of(new UserAuthorizationProjection(
                "teacher-1", TENANT, List.of("teacher"), List.of(), List.of(), List.of())));
        when(resolver.resolve(TENANT, CLASS_7A)).thenReturn(resolved(people(30)));

        assertThat(service.dispatchDue(TENANT, "c1")).isTrue();

        verify(guard).assertAllowed(TENANT, "teacher-1", Set.of("teacher"), CLASS_7A);
        assertThat(captureMessages()).hasSize(30);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.SENT);
        assertThat(campaign.getResolvedCount()).isEqualTo(30);
    }

    @Test
    void scheduledDispatchFailsTheCampaignWhenTheCreatorLostScope() {
        NotificationCampaignEntity campaign = campaign("c1", "teacher-1", CampaignStatus.SENDING, NotificationPriority.INFO);
        when(campaigns.claimDue(eq(TENANT), eq("c1"), any())).thenReturn(1);
        when(campaigns.findByTenantIdAndId(TENANT, "c1")).thenReturn(Optional.of(campaign));
        when(users.findByTenantIdAndIds(TENANT, List.of("teacher-1")))
                .thenReturn(List.of(new UserAccountProjection("teacher-1", TENANT, "GV", null, "SUSPENDED")));
        doThrow(new AccessDeniedException("no")).when(guard).assertAllowed(TENANT, "teacher-1", Set.of(), CLASS_7A);

        assertThat(service.dispatchDue(TENANT, "c1")).isFalse();

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.FAILED);
        verifyNoInteractions(resolver, messages);
    }

    private List<NotificationMessageEntity> captureMessages() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<NotificationMessageEntity>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(messages).saveAll(captor.capture());
        return List.copyOf(captor.getValue());
    }

    private List<NotificationOutboxEntity> captureOutbox() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationOutboxEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(outbox).saveAll(captor.capture());
        return captor.getValue();
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

    private static NotificationDeviceRegistrationEntity device(String userId) {
        NotificationDeviceRegistrationEntity device = new NotificationDeviceRegistrationEntity();
        device.setId("dev-" + userId);
        device.setTenantId(TENANT);
        device.setUserId(userId);
        device.setPortal("lms");
        device.setRegistrationToken("token-" + userId);
        device.setEnabled(true);
        return device;
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}

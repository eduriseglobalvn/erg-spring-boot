package com.erg.ergspringboot.notifications.internal.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.notifications.CampaignAudience;
import com.erg.ergspringboot.notifications.CampaignStatus;
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
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class CampaignFanOutTest {

    private static final String TENANT = "tenant_a";
    private static final Instant NOW = Instant.parse("2026-11-07T03:00:00Z");
    private static final OffsetDateTime NOW_AT = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final CampaignAudience CLASS_7A =
            new CampaignAudience(false, List.of(), List.of(), List.of("class-7a"), List.of(), List.of(), List.of());

    @Mock private NotificationCampaignRepository campaigns;
    @Mock private NotificationAudienceResolver resolver;
    @Mock private NotificationMessageRepository messages;
    @Mock private NotificationRecipientRepository recipients;
    @Mock private NotificationDeviceRegistrationRepository devices;
    @Mock private NotificationOutboxRepository outbox;
    @Mock private NotificationPreferenceRepository preferences;
    @Mock private NotificationMetrics metrics;

    /** What the unique (tenant_id, dedupe_key) index would hold. */
    private final List<NotificationMessageEntity> stored = new ArrayList<>();
    private final AtomicInteger saveCalls = new AtomicInteger();
    private int failOnSaveCall = -1;
    private CampaignFanOut fanOut;

    @BeforeEach
    void setUp() {
        NotificationPushProperties fcm = new NotificationPushProperties();
        fcm.setProjectId("erg-test");
        fcm.getCredentials().setStaticAccessToken("test-token");
        NotificationCommandService commands = new NotificationCommandService(messages, recipients, devices, outbox,
                fcm, new NotificationTokenCipher(new NotificationPushProperties()), metrics, preferences);
        fanOut = new CampaignFanOut(campaigns, resolver, commands, TransactionOperations.withoutTransaction(), null,
                Runnable::run, Clock.fixed(NOW, ZoneOffset.UTC), 2);
        lenient().when(messages.findByTenantIdAndDedupeKeyIn(eq(TENANT), any())).thenAnswer(call -> {
            Collection<String> keys = call.getArgument(1);
            return stored.stream().filter(message -> keys.contains(message.getDedupeKey())).toList();
        });
        lenient().when(messages.saveAll(any())).thenAnswer(call -> {
            if (saveCalls.incrementAndGet() == failOnSaveCall) {
                throw new IllegalStateException("db down");
            }
            Collection<NotificationMessageEntity> saved = call.getArgument(0);
            saved.forEach(message -> message.setId("msg-" + stored.size()));
            stored.addAll(saved);
            return new ArrayList<>(saved);
        });
        lenient().when(campaigns.completeSending(eq(TENANT), eq("c1"), anyInt(), any())).thenReturn(1);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void writesEveryRecipientInChunksThenMarksSent() {
        sending(CampaignStatus.SENDING, NotificationPriority.INFO);
        when(resolver.resolve(TENANT, CLASS_7A)).thenReturn(resolved(people(5)));

        assertThat(fanOut.run(TENANT, "c1")).isTrue();

        assertThat(saveCalls).hasValue(3);
        assertThat(stored).hasSize(5).allSatisfy(message -> {
            assertThat(message.getCampaignId()).isEqualTo("c1");
            assertThat(message.getDedupeKey()).isEqualTo("campaign:c1:" + userOf(message));
        });
        verify(campaigns).completeSending(TENANT, "c1", 5, NOW_AT);
    }

    @Test
    void aCrashBetweenChunksResumesWithoutSendingAnyoneTwice() {
        sending(CampaignStatus.SENDING, NotificationPriority.INFO);
        when(resolver.resolve(TENANT, CLASS_7A)).thenReturn(resolved(people(5)));
        failOnSaveCall = 2;

        // Chunk 1 (u0,u1) is committed, chunk 2 fails, the campaign is left SENDING.
        assertThatThrownBy(() -> fanOut.run(TENANT, "c1")).isInstanceOf(IllegalStateException.class);
        assertThat(stored).hasSize(2);
        verify(campaigns, never()).completeSending(any(), any(), anyInt(), any());

        // The next pass resolves again; u0,u1 are skipped by their dedupe keys, the rest are written once.
        assertThat(fanOut.run(TENANT, "c1")).isTrue();

        assertThat(stored.stream().map(CampaignFanOutTest::userOf)).containsExactly("u0", "u1", "u2", "u3", "u4");
        assertThat(stored.stream().map(NotificationMessageEntity::getDedupeKey)).doesNotHaveDuplicates();
        verify(campaigns).completeSending(TENANT, "c1", 5, NOW_AT);
    }

    @Test
    void aRunOverAnAlreadyFinishedFanOutWritesNothingNew() {
        sending(CampaignStatus.SENDING, NotificationPriority.INFO);
        when(resolver.resolve(TENANT, CLASS_7A)).thenReturn(resolved(people(3)));
        fanOut.run(TENANT, "c1");

        fanOut.run(TENANT, "c1");

        assertThat(stored).hasSize(3);
        assertThat(stored.stream().map(NotificationMessageEntity::getDedupeKey)).doesNotHaveDuplicates();
    }

    @Test
    void pushOutboxFollowsTheMessageAndUrgentBypassesOptOuts() {
        sending(CampaignStatus.SENDING, NotificationPriority.URGENT);
        when(resolver.resolve(TENANT, CLASS_7A)).thenReturn(resolved(people(2)));
        when(devices.findByTenantIdAndUserIdInAndEnabledTrue(eq(TENANT), any()))
                .thenReturn(List.of(device("u0"), device("u1")));

        fanOut.run(TENANT, "c1");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<NotificationOutboxEntity>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(outbox).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2)
                .allSatisfy(entry -> assertThat(entry.getPayload()).containsEntry("bypassPreferences", "true"));
        verifyNoInteractions(preferences);
    }

    @Test
    void nonUrgentPushHonoursTheOptOutOfThatPortal() {
        sending(CampaignStatus.SENDING, NotificationPriority.IMPORTANT);
        when(resolver.resolve(TENANT, CLASS_7A)).thenReturn(resolved(people(2)));
        NotificationPreferenceEntity optOut = new NotificationPreferenceEntity();
        optOut.setUserId("u1");
        optOut.setPortal("lms");
        optOut.setPushEnabled(false);
        when(preferences.findByTenantIdAndEventTypeAndUserIdIn(eq(TENANT), eq("CAMPAIGN"), any()))
                .thenReturn(List.of(optOut));
        when(devices.findByTenantIdAndUserIdInAndEnabledTrue(eq(TENANT), any()))
                .thenReturn(List.of(device("u0"), device("u1")));

        fanOut.run(TENANT, "c1");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<NotificationOutboxEntity>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(outbox).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .satisfies(entry -> assertThat(entry.getPayload()).containsEntry("deviceRegistrationId", "dev-u0"));
        assertThat(stored).hasSize(2);
    }

    @Test
    void anAudienceThatNowResolvesToNobodyEndsFailed() {
        sending(CampaignStatus.SENDING, NotificationPriority.INFO);
        when(resolver.resolve(TENANT, CLASS_7A)).thenReturn(resolved(List.of()));

        assertThat(fanOut.run(TENANT, "c1")).isFalse();

        verify(campaigns).transition(TENANT, "c1", List.of(CampaignStatus.SENDING), CampaignStatus.FAILED, NOW_AT);
        verifyNoInteractions(messages);
    }

    @Test
    void aCampaignStuckSendingForTooLongEndsFailedInsteadOfRetryingForEver() {
        NotificationCampaignEntity campaign = sending(CampaignStatus.SENDING, NotificationPriority.INFO);
        campaign.setUpdatedAt(NOW_AT.minus(CampaignFanOut.GIVE_UP_AFTER).minusMinutes(1));

        assertThat(fanOut.run(TENANT, "c1")).isFalse();

        verify(campaigns).transition(TENANT, "c1", List.of(CampaignStatus.SENDING), CampaignStatus.FAILED, NOW_AT);
        verifyNoInteractions(resolver, messages);
    }

    @Test
    void onlySendingCampaignsAreFannedOut() {
        for (CampaignStatus status : List.of(CampaignStatus.SENT, CampaignStatus.CANCELLED, CampaignStatus.DRAFT)) {
            sending(status, NotificationPriority.INFO);

            assertThat(fanOut.run(TENANT, "c1")).isFalse();
        }
        verifyNoInteractions(resolver, messages);
    }

    @Test
    void theSendNowTriggerWaitsForTheCommitThenFansOut() {
        sending(CampaignStatus.SENDING, NotificationPriority.INFO);
        when(resolver.resolve(TENANT, CLASS_7A)).thenReturn(resolved(people(1)));
        TransactionSynchronizationManager.initSynchronization();

        fanOut.triggerAfterCommit(TENANT, "c1");

        verifyNoInteractions(campaigns);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(campaigns).completeSending(TENANT, "c1", 1, NOW_AT);
    }

    private NotificationCampaignEntity sending(CampaignStatus status, NotificationPriority priority) {
        NotificationCampaignEntity campaign = new NotificationCampaignEntity();
        campaign.setId("c1");
        campaign.setTenantId(TENANT);
        campaign.setCreatedBy("admin-1");
        campaign.setSenderRole("ADMIN");
        campaign.setTitle("Họp phụ huynh");
        campaign.setBody("Nội dung");
        campaign.setPriority(priority);
        campaign.setAudience(CLASS_7A);
        campaign.setStatus(status);
        when(campaigns.findByTenantIdAndId(TENANT, "c1")).thenReturn(Optional.of(campaign));
        return campaign;
    }

    private static String userOf(NotificationMessageEntity message) {
        return message.getDedupeKey().substring("campaign:c1:".length());
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
}

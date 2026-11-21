package com.erg.ergspringboot.notifications.internal.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.notifications.persistence.NotificationCampaignEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationMessageRepository;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NotificationCampaignQueryServiceTest {

    private final NotificationCampaignRepository campaigns = mock(NotificationCampaignRepository.class);
    private final NotificationMessageRepository messages = mock(NotificationMessageRepository.class);
    private final CampaignCounterReader counters = mock(CampaignCounterReader.class);
    private final AudienceScopeGuard guard = new AudienceScopeGuard(mock(com.erg.ergspringboot.lms.LmsAudienceReadPort.class),
            mock(com.erg.ergspringboot.users.UserAuthorizationPort.class));
    private final NotificationCampaignQueryService queries =
            new NotificationCampaignQueryService(campaigns, messages, guard, mock(UserDirectoryPort.class), counters);

    @Test
    void unackedOnACampaignWithoutAcknowledgementIsEmpty() {
        NotificationCampaignEntity campaign = new NotificationCampaignEntity();
        campaign.setId("c1");
        campaign.setTenantId("t1");
        campaign.setCreatedBy("admin-1");
        campaign.setRequireReadAck(false);
        when(campaigns.findByTenantIdAndId("t1", "c1")).thenReturn(Optional.of(campaign));

        var page = queries.recipients(new CampaignActor("t1", "admin-1", Set.of("admin")), "c1", "UNACKED", 0, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isZero();
        verifyNoInteractions(messages);
    }

    @Test
    void theResponseCountsTheHandPickedUsersSoAListNeedNotReadThem() {
        NotificationCampaignEntity campaign = new NotificationCampaignEntity();
        campaign.setId("c1");
        campaign.setTenantId("t1");
        campaign.setAudience(new com.erg.ergspringboot.notifications.CampaignAudience(
                false, null, null, null, null, null, java.util.List.of("u1", "u2", "u3")));

        assertThat(queries.toResponse(campaign).userIdCount()).isEqualTo(3);
        assertThat(queries.toResponse(campaign).audience().userIds()).hasSize(3);
    }

    @Test
    void summaryViewDropsTheAudienceIdListsButKeepsTheCounts() {
        NotificationCampaignEntity campaign = new NotificationCampaignEntity();
        campaign.setId("c1");
        campaign.setTenantId("t1");
        campaign.setCreatedBy("admin-1");
        campaign.setAudience(new com.erg.ergspringboot.notifications.CampaignAudience(
                false, java.util.List.of("s1"), null, java.util.List.of("c7"), null, java.util.List.of("STUDENT"),
                java.util.List.of("u1", "u2")));
        when(campaigns.findPage(org.mockito.ArgumentMatchers.eq("t1"), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(campaign)));
        when(counters.load(org.mockito.ArgumentMatchers.eq("t1"), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(java.util.List.of("c1"))))
                .thenReturn(new CampaignCounterReader.Counters(java.util.List.of(
                        new com.erg.ergspringboot.notifications.persistence.projection.CampaignDeliveryCounts("c1", 2L, 1L, 0L))));
        CampaignActor admin = new CampaignActor("t1", "admin-1", Set.of("admin"));

        var lean = queries.list(admin, "SENT", false, 0, 20, "summary").items().getFirst();
        var full = queries.list(admin, "SENT", false, 0, 20, null).items().getFirst();

        assertThat(lean.audience().userIds()).isEmpty();
        assertThat(lean.audience().classIds()).isEmpty();
        assertThat(lean.audience().roles()).containsExactly("STUDENT");
        assertThat(lean.userIdCount()).isEqualTo(2);
        assertThat(lean.recipientCount()).isEqualTo(2);
        assertThat(full.audience().userIds()).containsExactly("u1", "u2");
    }
}

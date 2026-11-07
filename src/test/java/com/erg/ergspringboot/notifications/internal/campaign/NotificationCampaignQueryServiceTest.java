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
    private final AudienceScopeGuard guard = new AudienceScopeGuard(mock(com.erg.ergspringboot.lms.LmsAudienceReadPort.class),
            mock(com.erg.ergspringboot.users.UserAuthorizationPort.class));
    private final NotificationCampaignQueryService queries =
            new NotificationCampaignQueryService(campaigns, messages, guard, mock(UserDirectoryPort.class));

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
}

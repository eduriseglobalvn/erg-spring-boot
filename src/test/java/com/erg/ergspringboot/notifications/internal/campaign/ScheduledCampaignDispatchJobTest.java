package com.erg.ergspringboot.notifications.internal.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.notifications.CampaignStatus;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignRepository;
import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledCampaignDispatchJobTest {

    private static final Instant NOW = Instant.parse("2026-11-07T03:00:00Z");

    @Mock private NotificationCampaignRepository campaigns;
    @Mock private NotificationCampaignService service;
    @Mock private ClusterJobLock lock;

    @Test
    void dispatchesOnlyTheDueCampaignsInTheirOwnTenant() {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        // The repository is asked for campaigns due at "now"; only the due one comes back.
        when(campaigns.findTop100ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(CampaignStatus.SCHEDULED, now))
                .thenReturn(List.of(campaign("due", "tenant_b")));
        List<String> tenantsSeen = new ArrayList<>();
        when(service.dispatchDue("tenant_b", "due")).thenAnswer(invocation -> {
            tenantsSeen.add(TenantContext.current().tenantId());
            return true;
        });

        int sent = job(null).dispatchDue();

        assertThat(sent).isEqualTo(1);
        assertThat(tenantsSeen).containsExactly("tenant_b");
        verify(service, never()).dispatchDue(eq("tenant_b"), eq("later"));
    }

    @Test
    void aLostClaimIsNotCountedAndAFailureMarksTheCampaignFailedAndMovesOn() {
        when(campaigns.findTop100ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(eq(CampaignStatus.SCHEDULED), any()))
                .thenReturn(List.of(campaign("taken", "t1"), campaign("boom", "t1"), campaign("ok", "t2")));
        when(service.dispatchDue("t1", "taken")).thenReturn(false);
        when(service.dispatchDue("t1", "boom")).thenThrow(new IllegalStateException("db"));
        when(service.dispatchDue("t2", "ok")).thenReturn(true);

        assertThat(job(null).dispatchDue()).isEqualTo(1);

        verify(service).markFailed("t1", "boom");
        verify(service, never()).markFailed("t1", "taken");
    }

    @Test
    void runsUnderTheClusterLock() {
        when(lock.runExclusively(eq(ScheduledCampaignDispatchJob.JOB_NAME), any())).thenReturn(false);

        job(lock).run();

        verify(lock).runExclusively(eq(ScheduledCampaignDispatchJob.JOB_NAME), any());
        verify(campaigns, never()).findTop100ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(any(), any());
    }

    private ScheduledCampaignDispatchJob job(ClusterJobLock jobLock) {
        return new ScheduledCampaignDispatchJob(campaigns, service, jobLock, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static NotificationCampaignEntity campaign(String id, String tenant) {
        NotificationCampaignEntity campaign = new NotificationCampaignEntity();
        campaign.setId(id);
        campaign.setTenantId(tenant);
        campaign.setStatus(CampaignStatus.SCHEDULED);
        return campaign;
    }
}

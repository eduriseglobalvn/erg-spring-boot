package com.erg.ergspringboot.notifications.internal.campaign;

import com.erg.ergspringboot.notifications.CampaignStatus;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignRepository;
import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.TenantContext;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends scheduled campaigns once they are due and finishes every SENDING fan-out. One instance at a time (cluster
 * advisory lock); the due list is read across tenants in maintenance scope, each campaign is then claimed in its own
 * tenant scope and transaction. The service's conditional SCHEDULED -> SENDING update keeps a campaign from being
 * claimed twice; {@link CampaignFanOut} keeps a resumed fan-out from duplicating messages.
 */
@Component
public class ScheduledCampaignDispatchJob {

    static final String JOB_NAME = "notification-campaign-dispatch";
    private static final Logger log = LoggerFactory.getLogger(ScheduledCampaignDispatchJob.class);

    private final NotificationCampaignRepository campaigns;
    private final NotificationCampaignService service;
    private final CampaignFanOut fanOut;
    private final ClusterJobLock jobLock;
    private final Clock clock;

    @Autowired
    public ScheduledCampaignDispatchJob(NotificationCampaignRepository campaigns, NotificationCampaignService service,
            CampaignFanOut fanOut, ObjectProvider<ClusterJobLock> jobLock) {
        this(campaigns, service, fanOut, jobLock.getIfAvailable(), Clock.systemUTC());
    }

    ScheduledCampaignDispatchJob(NotificationCampaignRepository campaigns, NotificationCampaignService service,
            CampaignFanOut fanOut, ClusterJobLock jobLock, Clock clock) {
        this.campaigns = campaigns;
        this.service = service;
        this.fanOut = fanOut;
        this.jobLock = jobLock;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void run() {
        if (jobLock == null) {
            pass();
        } else {
            jobLock.runExclusively(JOB_NAME, this::pass);
        }
    }

    /** Claim the due scheduled campaigns, then fan out everything still SENDING (send-now ones the trigger missed too). */
    void pass() {
        dispatchDue();
        fanOut.drainSending();
    }

    /** Returns how many due campaigns were claimed (SCHEDULED -> SENDING) in this pass (at most 100; the rest wait). */
    public int dispatchDue() {
        List<NotificationCampaignEntity> due;
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            due = campaigns.findTop100ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                    CampaignStatus.SCHEDULED, OffsetDateTime.now(clock));
        }
        int sent = 0;
        for (NotificationCampaignEntity campaign : due) {
            String tenantId = campaign.getTenantId();
            try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.of(tenantId, null, "system"))) {
                if (service.dispatchDue(tenantId, campaign.getId())) {
                    sent++;
                }
            } catch (RuntimeException failure) {
                // The dispatch transaction rolled back; end the campaign so it is not retried every minute.
                log.warn("Scheduled campaign {} failed to dispatch: {}", campaign.getId(), failure.getMessage());
                try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.of(tenantId, null, "system"))) {
                    service.markFailed(tenantId, campaign.getId());
                }
            }
        }
        return sent;
    }
}

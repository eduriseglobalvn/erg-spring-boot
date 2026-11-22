package com.erg.ergspringboot.notifications.internal.campaign;

import com.erg.ergspringboot.notifications.CampaignStatus;
import com.erg.ergspringboot.notifications.internal.CampaignDelivery;
import com.erg.ergspringboot.notifications.internal.NotificationCommandService;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignRepository;
import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes a SENDING campaign's messages, recipients and push outbox rows in chunks of {@value #CHUNK} people, one
 * transaction per chunk, then flips the campaign to SENT. Resumable: the audience is resolved again on every run
 * and a person who already has the campaign's message (unique {@code uq_notification_messages_tenant_dedupe} on
 * {@code (tenant_id, dedupe_key)}, key {@code campaign:<id>:<userId>}) is skipped, so a crash between chunks never
 * double-sends. Callers hold the tenant scope; {@code run} is serialised by the cluster lock of the dispatch job.
 */
@Component
public class CampaignFanOut {

    static final int CHUNK = 1_000;
    /** A campaign still SENDING after this long is ended FAILED instead of being retried every minute for ever. */
    static final Duration GIVE_UP_AFTER = Duration.ofHours(6);
    private static final Logger log = LoggerFactory.getLogger(CampaignFanOut.class);

    private final NotificationCampaignRepository campaigns;
    private final NotificationAudienceResolver resolver;
    private final NotificationCommandService commands;
    private final TransactionOperations tx;
    private final ClusterJobLock jobLock;
    private final Executor executor;
    private final Clock clock;
    private final int chunk;

    @Autowired
    public CampaignFanOut(NotificationCampaignRepository campaigns, NotificationAudienceResolver resolver,
            NotificationCommandService commands, PlatformTransactionManager transactionManager,
            ObjectProvider<ClusterJobLock> jobLock, @Qualifier("moduleEventIoExecutor") Executor executor) {
        this(campaigns, resolver, commands, new TransactionTemplate(transactionManager), jobLock.getIfAvailable(),
                executor, Clock.systemUTC(), CHUNK);
    }

    CampaignFanOut(NotificationCampaignRepository campaigns, NotificationAudienceResolver resolver,
            NotificationCommandService commands, TransactionOperations tx, ClusterJobLock jobLock, Executor executor,
            Clock clock, int chunk) {
        this.campaigns = campaigns;
        this.resolver = resolver;
        this.commands = commands;
        this.tx = tx;
        this.jobLock = jobLock;
        this.executor = executor;
        this.clock = clock;
        this.chunk = chunk;
    }

    /** Send-now: start the fan-out as soon as the request's transaction has committed (the scheduler is the safety net). */
    public void triggerAfterCommit(String tenantId, String campaignId) {
        Runnable start = () -> executor.execute(() -> runExclusively(tenantId, campaignId));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            start.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                start.run();
            }
        });
    }

    /** The scheduler's pass: every unfinished campaign, already under the job's cluster lock. */
    public int drainSending() {
        List<NotificationCampaignEntity> sending;
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            sending = campaigns.findTop100ByStatusOrderByUpdatedAtAsc(CampaignStatus.SENDING);
        }
        int finished = 0;
        for (NotificationCampaignEntity campaign : sending) {
            if (runInScope(campaign.getTenantId(), campaign.getId())) {
                finished++;
            }
        }
        return finished;
    }

    private void runExclusively(String tenantId, String campaignId) {
        if (jobLock == null) {
            runInScope(tenantId, campaignId);
        } else {
            jobLock.runExclusively(ScheduledCampaignDispatchJob.JOB_NAME, () -> runInScope(tenantId, campaignId));
        }
    }

    private boolean runInScope(String tenantId, String campaignId) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.of(tenantId, null, "system"))) {
            return run(tenantId, campaignId);
        } catch (RuntimeException failure) {
            // Chunks already committed stay; the campaign stays SENDING and the next pass resumes it.
            log.warn("Campaign {} fan-out interrupted, will resume: {}", campaignId, failure.getMessage());
            return false;
        }
    }

    /** True when this call finished the campaign. */
    boolean run(String tenantId, String campaignId) {
        NotificationCampaignEntity campaign = tx.execute(
                status -> campaigns.findByTenantIdAndId(tenantId, campaignId).orElse(null));
        if (campaign == null || campaign.getStatus() != CampaignStatus.SENDING) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (campaign.getUpdatedAt() != null && campaign.getUpdatedAt().isBefore(now.minus(GIVE_UP_AFTER))) {
            log.warn("Campaign {} stayed SENDING for over {}; ending it FAILED", campaignId, GIVE_UP_AFTER);
            return fail(tenantId, campaignId);
        }
        List<String> userIds = tx.execute(status -> resolver.resolve(tenantId, campaign.getAudience()).userIds());
        if (userIds == null || userIds.isEmpty()) {
            return fail(tenantId, campaignId);
        }
        for (int from = 0; from < userIds.size(); from += chunk) {
            List<String> part = List.copyOf(userIds.subList(from, Math.min(userIds.size(), from + chunk)));
            tx.executeWithoutResult(status -> commands.enqueueCampaign(new CampaignDelivery(tenantId,
                    campaign.getCreatedBy(), campaignId, campaign.getTitle(), campaign.getBody(), campaign.getPriority(),
                    campaign.isPushEnabled(), part)));
        }
        Integer done = tx.execute(status -> campaigns.completeSending(tenantId, campaignId, userIds.size(), now));
        return done != null && done > 0;
    }

    private boolean fail(String tenantId, String campaignId) {
        tx.executeWithoutResult(status -> campaigns.transition(tenantId, campaignId, List.of(CampaignStatus.SENDING),
                CampaignStatus.FAILED, OffsetDateTime.now(clock)));
        return false;
    }
}

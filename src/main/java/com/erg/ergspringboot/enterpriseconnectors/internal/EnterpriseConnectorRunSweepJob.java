package com.erg.ergspringboot.enterpriseconnectors.internal;

import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorRunRepository;
import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.TenantContext;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Moves connector runs stuck in RUNNING (worker died mid provider call) to RETRYABLE_FAILED so they can be retried. */
@Component
public class EnterpriseConnectorRunSweepJob {
    static final String JOB_NAME = "enterprise-connector-run-sweep";
    private static final Logger log = LoggerFactory.getLogger(EnterpriseConnectorRunSweepJob.class);

    private final EnterpriseConnectorRunRepository runs;
    private final TransactionTemplate transaction;
    private final ClusterJobLock jobLock;
    private final Duration timeout;

    public EnterpriseConnectorRunSweepJob(
            EnterpriseConnectorRunRepository runs,
            @Qualifier("postgresTransactionManager") PlatformTransactionManager transactionManager,
            ObjectProvider<ClusterJobLock> jobLock,
            @Value("${erg.enterprise-connectors.run-timeout:PT30M}") Duration timeout) {
        this.runs = runs;
        this.transaction = new TransactionTemplate(transactionManager);
        this.jobLock = jobLock.getIfAvailable();
        this.timeout = timeout;
    }

    @Scheduled(fixedDelayString = "${erg.enterprise-connectors.run-sweep-delay:PT10M}",
            initialDelayString = "${erg.enterprise-connectors.run-sweep-delay:PT10M}")
    public void sweep() {
        if (jobLock == null) run();
        else jobLock.runExclusively(JOB_NAME, this::run);
    }

    int run() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // Reads and writes every tenant's runs, so it runs in maintenance mode like the other sweep jobs.
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            Integer reaped = transaction.execute(status -> runs.failStuckRuns(now.minus(timeout), now));
            int count = reaped == null ? 0 : reaped;
            if (count > 0) log.warn("Marked {} stuck enterprise connector run(s) retryable", count);
            return count;
        }
    }
}

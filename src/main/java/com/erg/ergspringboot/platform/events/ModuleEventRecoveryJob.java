package com.erg.ergspringboot.platform.events;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bounded recovery trigger for durable Modulith publications.
 *
 * <p>The registry remains the source of truth. This component only asks it to
 * resubmit old failed/incomplete rows; it never creates an in-memory queue or
 * dispatches a listener itself.</p>
 *
 * <p>Resubmission is bounded per publication: once a failed publication has used
 * {@code maxAttempts} completion attempts it is moved to {@code event_publication_quarantine}
 * (V119) and counted in {@code module.events.quarantined}, so a poison event stops retrying
 * and cannot block newer failures at the head of the registry's batch. Only one instance in
 * the cluster runs a recovery pass at a time.</p>
 */
@Component
@ConditionalOnProperty(name = "erg.module-events.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class ModuleEventRecoveryJob {

    static final String JOB_NAME = "module-event-recovery";
    static final String QUARANTINED_METRIC = "module.events.quarantined";
    private static final Logger log = LoggerFactory.getLogger(ModuleEventRecoveryJob.class);
    private static final String QUARANTINE_SQL = """
            with exhausted as (
                select id from event_publication
                where completion_date is null
                  and status = 'FAILED'
                  and coalesce(completion_attempts, 0) >= ?
                order by publication_date
                limit ?
            ), moved as (
                delete from event_publication publication
                using exhausted
                where publication.id = exhausted.id
                returning publication.*
            )
            insert into event_publication_quarantine (
                id, listener_id, event_type, serialized_event, publication_date, status,
                completion_attempts, last_resubmission_date, quarantined_at)
            select id, listener_id, event_type, serialized_event, publication_date, status,
                   completion_attempts, last_resubmission_date, now()
            from moved
            on conflict (id) do nothing
            """;

    private final FailedEventPublications failed;
    private final IncompleteEventPublications incomplete;
    private final int batchSize;
    private final int maxInFlight;
    private final Duration minAge;
    private final int maxAttempts;
    private final int quarantineBatchSize;
    private final JdbcTemplate jdbc;
    private final ClusterJobLock jobLock;
    private final MeterRegistry meterRegistry;
    private final AtomicBoolean running = new AtomicBoolean();

    @Autowired
    public ModuleEventRecoveryJob(FailedEventPublications failed, IncompleteEventPublications incomplete,
            ErgModuleEventsProperties properties, ObjectProvider<JdbcTemplate> jdbc,
            ObjectProvider<ClusterJobLock> jobLock, ObjectProvider<MeterRegistry> meterRegistry) {
        this(failed, incomplete, properties.getRecovery().getBatchSize(),
                properties.getRecovery().getMaxInFlight(), properties.getRecovery().getMinAge(),
                properties.getRecovery().getMaxAttempts(), properties.getRecovery().getQuarantineBatchSize(),
                jdbc.getIfAvailable(), jobLock.getIfAvailable(), meterRegistry.getIfAvailable());
        properties.validate();
    }

    /** Compatibility constructor for focused unit tests and old embedders (no quarantine, no cluster lock). */
    public ModuleEventRecoveryJob(FailedEventPublications failed, IncompleteEventPublications incomplete,
            int batchSize, int maxInFlight, Duration minAge) {
        this(failed, incomplete, batchSize, maxInFlight, minAge, 10, 500, null, null, null);
    }

    public ModuleEventRecoveryJob(FailedEventPublications failed, IncompleteEventPublications incomplete,
            int batchSize, int maxInFlight, Duration minAge, int maxAttempts, int quarantineBatchSize,
            JdbcTemplate jdbc, ClusterJobLock jobLock, MeterRegistry meterRegistry) {
        if (batchSize < 1 || batchSize > 1000 || maxInFlight < 1 || maxInFlight > 128 || minAge.isNegative()
                || maxAttempts < 1 || maxAttempts > 1000 || quarantineBatchSize < 1 || quarantineBatchSize > 10000) {
            throw new IllegalArgumentException("Invalid Modulith event recovery bounds");
        }
        this.failed = failed;
        this.incomplete = incomplete;
        this.batchSize = batchSize;
        this.maxInFlight = maxInFlight;
        this.minAge = minAge;
        this.maxAttempts = maxAttempts;
        this.quarantineBatchSize = quarantineBatchSize;
        this.jdbc = jdbc;
        this.jobLock = jobLock;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${erg.module-events.recovery.fixed-delay:30000}")
    public void recover() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            if (jobLock == null) {
                recoverOnce();
            } else {
                jobLock.runExclusively(JOB_NAME, this::recoverOnce);
            }
        } finally {
            running.set(false);
        }
    }

    private void recoverOnce() {
        quarantineExhausted();
        ResubmissionOptions options = ResubmissionOptions.defaults()
                .withBatchSize(batchSize)
                .withMaxInFlight(maxInFlight)
                .withMinAge(minAge)
                .withFilter(publication -> publication.getCompletionAttempts() < maxAttempts);
        resubmitFailed(options);
        resubmitIncomplete(options);
    }

    int quarantineExhausted() {
        if (jdbc == null) {
            return 0;
        }
        try {
            int moved = jdbc.update(QUARANTINE_SQL, maxAttempts, quarantineBatchSize);
            if (moved > 0) {
                log.warn("Quarantined {} Modulith event publication(s) after {} completion attempts", moved, maxAttempts);
                if (meterRegistry != null) {
                    meterRegistry.counter(QUARANTINED_METRIC).increment(moved);
                }
            }
            return moved;
        } catch (RuntimeException exception) {
            log.warn("Failed to quarantine exhausted Modulith event publications", exception);
            return 0;
        }
    }

    private void resubmitFailed(ResubmissionOptions options) {
        try {
            failed.resubmit(options);
        } catch (RuntimeException exception) {
            log.warn("Failed to resubmit Modulith failed event publications", exception);
        }
    }

    private void resubmitIncomplete(ResubmissionOptions options) {
        try {
            incomplete.resubmitIncompletePublications(options);
        } catch (RuntimeException exception) {
            log.warn("Failed to resubmit Modulith incomplete event publications", exception);
        }
    }
}

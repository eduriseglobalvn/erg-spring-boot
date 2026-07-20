package com.erg.ergspringboot.platform.events;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bounded recovery trigger for durable Modulith publications.
 *
 * <p>The registry remains the source of truth. This component only asks it to
 * resubmit old failed/incomplete rows; it never creates an in-memory queue or
 * dispatches a listener itself.</p>
 */
@Component
@ConditionalOnProperty(name = "erg.module-events.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class ModuleEventRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(ModuleEventRecoveryJob.class);

    private final FailedEventPublications failed;
    private final IncompleteEventPublications incomplete;
    private final int batchSize;
    private final int maxInFlight;
    private final Duration minAge;
    private final AtomicBoolean running = new AtomicBoolean();

    public ModuleEventRecoveryJob(FailedEventPublications failed, IncompleteEventPublications incomplete,
            @Value("${erg.module-events.recovery.batch-size:100}") int batchSize,
            @Value("${erg.module-events.recovery.max-in-flight:16}") int maxInFlight,
            @Value("${erg.module-events.recovery.min-age:30s}") Duration minAge) {
        if (batchSize < 1 || batchSize > 1000 || maxInFlight < 1 || maxInFlight > 128 || minAge.isNegative()) {
            throw new IllegalArgumentException("Invalid Modulith event recovery bounds");
        }
        this.failed = failed;
        this.incomplete = incomplete;
        this.batchSize = batchSize;
        this.maxInFlight = maxInFlight;
        this.minAge = minAge;
    }

    @Scheduled(fixedDelayString = "${erg.module-events.recovery.fixed-delay:30000}")
    public void recover() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            ResubmissionOptions options = ResubmissionOptions.defaults()
                    .withBatchSize(batchSize)
                    .withMaxInFlight(maxInFlight)
                    .withMinAge(minAge);
            resubmitFailed(options);
            resubmitIncomplete(options);
        } finally {
            running.set(false);
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

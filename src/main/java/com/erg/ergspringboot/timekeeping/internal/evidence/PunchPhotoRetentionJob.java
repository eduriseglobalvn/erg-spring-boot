package com.erg.ergspringboot.timekeeping.internal.evidence;

import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository.PhotoPurgeRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes punch selfies older than the retention (default 60 days) across tenants. The object goes
 * first; the key is cleared only after a successful delete, so a failed delete is retried next run.
 */
@Component
public class PunchPhotoRetentionJob {

    static final String JOB_NAME = "timekeeping-photo-retention";
    private static final Logger log = LoggerFactory.getLogger(PunchPhotoRetentionJob.class);

    private final TkPunchRepository punches;
    private final PrivateObjectStorage storage;
    private final Clock clock;
    private final ClusterJobLock jobLock;
    private final Duration retention;
    private final int batchSize;

    @Autowired
    PunchPhotoRetentionJob(TkPunchRepository punches, PrivateObjectStorage storage, Clock clock, ClusterJobLock jobLock,
            @Value("${erg.timekeeping.photos.retention-days:60}") int retentionDays) {
        this(punches, storage, clock, jobLock, retentionDays, 200);
    }

    PunchPhotoRetentionJob(TkPunchRepository punches, PrivateObjectStorage storage, Clock clock, ClusterJobLock jobLock,
            int retentionDays, int batchSize) {
        this.punches = punches;
        this.storage = storage;
        this.clock = clock;
        this.jobLock = jobLock;
        this.retention = Duration.ofDays(retentionDays);
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(cron = "${erg.timekeeping.photos.purge-cron:0 30 3 * * *}")
    public void purge() {
        jobLock.callExclusively(JOB_NAME, () -> purgeOlderThan(retention));
    }

    public int purgeOlderThan(Duration age) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(age);
        int purged = 0;
        int failed = 0;
        String afterId = "";
        List<PhotoPurgeRef> batch;
        do {
            try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
                batch = punches.findPhotoPurgeCandidates(cutoff, afterId, batchSize);
            }
            for (PhotoPurgeRef ref : batch) {
                try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.of(ref.getTenantId(), null, "system"))) {
                    storage.delete(ref.getPhotoKey());
                    purged += punches.markPhotoPurged(ref.getId(), ref.getTenantId(), ref.getPhotoKey(), now);
                } catch (RuntimeException ex) {
                    failed++;
                    // Class name only: storage errors can echo the object key.
                    log.warn("Punch photo purge failed for punch {}: {}", ref.getId(), ex.getClass().getSimpleName());
                }
            }
            if (!batch.isEmpty()) afterId = batch.get(batch.size() - 1).getId();
        } while (batch.size() == batchSize);
        if (purged > 0 || failed > 0) log.info("TIMEKEEPING_PHOTO_PURGE purged={} failed={}", purged, failed);
        return purged;
    }
}

package com.erg.ergspringboot.auth.internal.refresh;

import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.users.persistence.auth.RefreshTokenHistoryRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenHistoryRetentionJob {
    static final String JOB_NAME = "refresh-token-history-retention";

    private final RefreshTokenHistoryRepository repository;
    private final ClusterJobLock jobLock;

    public RefreshTokenHistoryRetentionJob(RefreshTokenHistoryRepository repository) {
        this(repository, (ClusterJobLock) null);
    }

    @Autowired
    public RefreshTokenHistoryRetentionJob(RefreshTokenHistoryRepository repository,
            ObjectProvider<ClusterJobLock> jobLock) {
        this(repository, jobLock.getIfAvailable());
    }

    RefreshTokenHistoryRetentionJob(RefreshTokenHistoryRepository repository, ClusterJobLock jobLock) {
        this.repository = repository;
        this.jobLock = jobLock;
    }

    @Scheduled(cron = "${erg.auth.refresh-token-history.retention-cron:0 20 3 * * *}")
    public void deleteExpiredHistory() {
        // One instance runs the purge, like the other retention jobs (P1-7).
        if (jobLock == null) {
            purge();
        } else {
            jobLock.runExclusively(JOB_NAME, this::purge);
        }
    }

    private void purge() {
        repository.deleteByConsumedAtBefore(OffsetDateTime.now(ZoneOffset.UTC).minusDays(30));
    }
}

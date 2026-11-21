package com.erg.ergspringboot.users.internal.lifecycle;

import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.users.persistence.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Anonymises accounts whose 30-day restore window has passed. One instance runs it (cluster lock) and each account
 * is anonymised in its own transaction, so one failure does not roll back the others.
 */
@Component
public class UserPurgeJob {
    static final String JOB_NAME = "user-purge";
    private static final Logger log = LoggerFactory.getLogger(UserPurgeJob.class);

    private final UserRepository users;
    private final UserSoftDeleteStore store;
    private final ClusterJobLock jobLock;
    private final TransactionTemplate transaction;

    @Autowired
    public UserPurgeJob(UserRepository users, UserSoftDeleteStore store, ObjectProvider<ClusterJobLock> jobLock,
            PlatformTransactionManager transactionManager) {
        this(users, store, jobLock.getIfAvailable(), new TransactionTemplate(transactionManager));
    }

    UserPurgeJob(UserRepository users, UserSoftDeleteStore store) {
        this(users, store, (ClusterJobLock) null, (TransactionTemplate) null);
    }

    UserPurgeJob(UserRepository users, UserSoftDeleteStore store, ClusterJobLock jobLock, TransactionTemplate transaction) {
        this.users = users;
        this.store = store;
        this.jobLock = jobLock;
        this.transaction = transaction;
    }

    @Scheduled(cron = "${erg.users.purge-cron:0 30 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void purgeDue() {
        if (jobLock == null) {
            purgeDue(OffsetDateTime.now(ZoneOffset.UTC));
        } else {
            jobLock.runExclusively(JOB_NAME, () -> purgeDue(OffsetDateTime.now(ZoneOffset.UTC)));
        }
    }

    /** Returns how many accounts were handled; a failing one is logged and left for the next run. */
    int purgeDue(OffsetDateTime now) {
        // users is tenant-protected (RLS) and a scheduled job has no tenant.
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            List<String> due = store.dueForPurge(now);
            int done = 0;
            for (String id : due) {
                try {
                    if (transaction == null) purgeOne(id); else transaction.executeWithoutResult(status -> purgeOne(id));
                    done++;
                } catch (RuntimeException ex) {
                    log.warn("User purge skipped {}: {}", id, ex.getMessage());
                }
            }
            return done;
        }
    }

    private void purgeOne(String id) {
        users.findById(id).ifPresent(user -> {
            if (UserLifecycleUseCase.STATUS_DELETED.equalsIgnoreCase(user.getStatus())) {
                UserLifecycleUseCase.anonymise(user);
                users.save(user);
            }
        });
        store.finishPurge(id);
    }
}

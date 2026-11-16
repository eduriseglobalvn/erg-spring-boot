package com.erg.ergspringboot.users.internal.lifecycle;

import com.erg.ergspringboot.users.persistence.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Anonymises accounts whose 30-day restore window has passed. Idempotent, so concurrent instances are harmless. */
@Component
public class UserPurgeJob {
    private final UserRepository users;
    private final UserSoftDeleteStore store;

    public UserPurgeJob(UserRepository users, UserSoftDeleteStore store) {
        this.users = users;
        this.store = store;
    }

    @Scheduled(cron = "${erg.users.purge-cron:0 30 3 * * *}")
    @Transactional
    public int purgeDue() {
        return purgeDue(OffsetDateTime.now(ZoneOffset.UTC));
    }

    int purgeDue(OffsetDateTime now) {
        List<String> due = store.dueForPurge(now);
        due.forEach(id -> {
            users.findById(id).ifPresent(user -> {
                if (UserLifecycleUseCase.STATUS_DELETED.equalsIgnoreCase(user.getStatus())) {
                    UserLifecycleUseCase.anonymise(user);
                    users.save(user);
                }
            });
            store.finishPurge(id);
        });
        return due.size();
    }
}

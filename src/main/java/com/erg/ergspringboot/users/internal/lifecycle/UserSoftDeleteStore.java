package com.erg.ergspringboot.users.internal.lifecycle;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Soft-delete markers of {@code users} (columns added by V161). Kept out of the entity on purpose. */
@Component
public class UserSoftDeleteStore {
    private final JdbcTemplate jdbc;

    public UserSoftDeleteStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void mark(Collection<String> userIds, OffsetDateTime deletedAt, OffsetDateTime purgeAfter) {
        userIds.forEach(id -> jdbc.update("update users set deleted_at = ?, purge_after = ? where id = ?",
                deletedAt, purgeAfter, id));
    }

    /** The end of the restore window, or empty when the account was never soft-deleted or was already purged. */
    public Optional<OffsetDateTime> purgeAfter(String userId) {
        return jdbc.queryForList("select purge_after from users where id = ? and purge_after is not null",
                OffsetDateTime.class, userId).stream().findFirst();
    }

    public void restore(String userId) {
        jdbc.update("update users set deleted_at = null, purge_after = null where id = ?", userId);
    }

    public List<String> dueForPurge(OffsetDateTime now) {
        return jdbc.queryForList("select id from users where status = 'DELETED' and purge_after is not null"
                + " and purge_after <= ? order by purge_after limit 500", String.class, now);
    }

    /** Called once the account is anonymised: it can no longer be restored. */
    public void finishPurge(String userId) {
        jdbc.update("update users set purge_after = null where id = ?", userId);
    }
}

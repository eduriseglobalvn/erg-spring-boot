package com.erg.ergspringboot.auth;

import com.erg.ergspringboot.auth.internal.credential.PasswordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ENV-002: gives the seeded admin a first password the installer knows. While the admin row still carries the
 * migration's temporary hash (nobody has changed it), the password becomes {@code ERG_BOOTSTRAP_ADMIN_PASSWORD}
 * and must be changed at first login. A password that was ever changed is never touched again.
 */
@Component
public class BootstrapAdminPasswordInitializer {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminPasswordInitializer.class);
    /** The hash V110 leaves on usr_super_admin; its plain text is not documented anywhere. */
    static final String MIGRATION_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$QUckidyuyvIcpAKkPuaI7Q$fX5Bt5G/LRjhWX3eDrH6J4w8bUG0pY1ZhZja+mLpKWY";

    private final JdbcTemplate jdbc;
    private final PasswordService passwords;
    private final String bootstrapPassword;

    public BootstrapAdminPasswordInitializer(JdbcTemplate jdbc, PasswordService passwords,
            @Value("${erg.bootstrap.admin-password:}") String bootstrapPassword) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.bootstrapPassword = bootstrapPassword;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onReady() {
        apply();
    }

    /** Returns true when the admin password was set. */
    @Transactional
    public boolean apply() {
        // users is tenant-protected (RLS) and startup has no tenant: without this the runtime role sees no admin row.
        jdbc.queryForObject("select set_config('app.maintenance_mode', 'true', true)", String.class);
        Integer untouched = jdbc.queryForObject(
                "select count(*) from users where id = 'usr_super_admin' and password_hash = ?", Integer.class, MIGRATION_HASH);
        if (untouched == null || untouched == 0 || bootstrapPassword == null || bootstrapPassword.isBlank()) {
            return false;
        }
        // The hash is compared again in the UPDATE, so two instances starting together cannot overwrite each other.
        boolean set = jdbc.update("""
                update users set password_hash = ?, must_change_password = true, updated_at = now()
                where id = 'usr_super_admin' and password_hash = ?
                """, passwords.hash(bootstrapPassword), MIGRATION_HASH) > 0;
        if (set) log.info("Bootstrap admin password set from ERG_BOOTSTRAP_ADMIN_PASSWORD; it must be changed at first login");
        return set;
    }
}

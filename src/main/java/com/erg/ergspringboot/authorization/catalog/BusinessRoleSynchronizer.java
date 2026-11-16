package com.erg.ergspringboot.authorization.catalog;

import com.erg.ergspringboot.authorization.catalog.PermissionCatalog.PermissionDef;
import com.erg.ergspringboot.platform.security.PrincipalCache;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes {@link PermissionCatalog} and {@link BusinessRoleCatalog} into the database at startup.
 * Roles outside the catalog are left alone (the V160 migration removes them) unless {@code erg.rbac.strict=true},
 * which stops startup instead. Permissions outside the catalog are never deleted here.
 */
@Component
public class BusinessRoleSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(BusinessRoleSynchronizer.class);
    private static final long ADVISORY_LOCK_KEY = 7_160_001L;

    private final JdbcTemplate jdbc;
    private final PrincipalCache principalCache;
    private final boolean strict;

    public BusinessRoleSynchronizer(
            JdbcTemplate jdbc,
            PrincipalCache principalCache,
            @Value("${erg.rbac.strict:false}") boolean strict) {
        this.jdbc = jdbc;
        this.principalCache = principalCache;
        this.strict = strict;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onReady() {
        sync();
    }

    /** Returns true when anything in the database changed. */
    @Transactional
    public boolean sync() {
        jdbc.queryForList("select 1 from (select pg_advisory_xact_lock(?)) t", Integer.class, ADVISORY_LOCK_KEY);
        checkForeignRoles();
        boolean changed = syncPermissions();
        changed |= syncRoles();
        if (changed) {
            log.info("Business roles synchronised from the catalog; principal cache evicted");
            principalCache.evictAll();
        }
        return changed;
    }

    private void checkForeignRoles() {
        Set<String> known = new HashSet<>();
        BusinessRoleCatalog.all().forEach(role -> known.add(role.id()));
        List<String> foreign = jdbc.queryForList("select id from roles", String.class).stream()
                .filter(id -> !known.contains(id)).toList();
        if (foreign.isEmpty()) {
            return;
        }
        if (strict) {
            throw new IllegalStateException("Roles outside the business role catalog: " + foreign);
        }
        log.warn("Roles outside the business role catalog (left untouched until V160 runs): {}", foreign);
    }

    private boolean syncPermissions() {
        boolean changed = false;
        for (PermissionDef def : PermissionCatalog.all()) {
            String id = "perm_" + def.name().replaceAll("[^a-z0-9]", "_");
            // permissions.id is varchar(36): long names get a stable name-based UUID instead
            if (id.length() > 36) id = java.util.UUID.nameUUIDFromBytes(def.name().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            changed |= jdbc.update("""
                    insert into permissions (id, created_at, updated_at, name, is_system, status)
                    values (?, now(), now(), ?, true, 'ACTIVE')
                    on conflict do nothing
                    """, id, def.name()) > 0;
        }
        return changed;
    }

    private boolean syncRoles() {
        boolean changed = false;
        for (BusinessRole role : BusinessRoleCatalog.all()) {
            changed |= jdbc.update("""
                    insert into roles (id, created_at, updated_at, name, description, is_default)
                    values (?, now(), now(), ?, ?, false)
                    on conflict (id) do update
                        set name = excluded.name, description = excluded.description, updated_at = now()
                        where roles.name is distinct from excluded.name
                           or roles.description is distinct from excluded.description
                    """, role.id(), role.code(), role.description()) > 0;
            changed |= syncRolePermissions(role);
        }
        return changed;
    }

    private boolean syncRolePermissions(BusinessRole role) {
        Set<String> current = new HashSet<>(jdbc.queryForList("""
                select p.name from role_permissions rp join permissions p on p.id = rp.permission_id
                where rp.role_id = ?
                """, String.class, role.id()));
        boolean changed = false;
        for (String name : current) {
            if (!role.permissions().contains(name)) {
                changed |= jdbc.update("""
                        delete from role_permissions
                        where role_id = ? and permission_id = (select id from permissions where name = ?)
                        """, role.id(), name) > 0;
            }
        }
        for (String name : role.permissions()) {
            if (!current.contains(name)) {
                changed |= jdbc.update("""
                        insert into role_permissions (role_id, permission_id)
                        select ?, id from permissions where name = ?
                        on conflict do nothing
                        """, role.id(), name) > 0;
            }
        }
        return changed;
    }
}

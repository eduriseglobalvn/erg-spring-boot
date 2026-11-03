package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.utils.IdGenerator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Durable (tenant, issuer, subject) -> user links for the OIDC BFF. Each call runs in its own
 * tenant-scoped transaction so the tenant RLS policy on {@code user_identity_links} applies.
 */
@Component
@ConditionalOnProperty(name = "erg.auth.bff-enabled", havingValue = "true")
public class OidcIdentityLinkStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public OidcIdentityLinkStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Optional<String> linkedUserId(String tenantId, String issuer, String subject) {
        return inTenant(tenantId, () -> jdbc.queryForList(
                "select user_id from user_identity_links where tenant_id = ? and issuer = ? and subject = ?",
                String.class, tenantId, issuer, subject).stream().findFirst());
    }

    /**
     * Pins {@code subject} to {@code userId}. Returns {@code false} when the user is already linked
     * to a different subject of the same issuer, which means this IdP account must not take it over.
     */
    public boolean link(String tenantId, String issuer, String subject, String userId) {
        return Boolean.TRUE.equals(inTenant(tenantId, () -> {
            List<String> existing = jdbc.queryForList(
                    "select subject from user_identity_links where tenant_id = ? and issuer = ? and user_id = ?",
                    String.class, tenantId, issuer, userId);
            if (!existing.isEmpty()) {
                return existing.contains(subject);
            }
            jdbc.update("insert into user_identity_links (id, tenant_id, user_id, issuer, subject, created_at) "
                            + "values (?, ?, ?, ?, ?, now()) on conflict do nothing",
                    IdGenerator.newId(), tenantId, userId, issuer, subject);
            return userId.equals(jdbc.queryForList(
                    "select user_id from user_identity_links where tenant_id = ? and issuer = ? and subject = ?",
                    String.class, tenantId, issuer, subject).stream().findFirst().orElse(null));
        }));
    }

    private <T> T inTenant(String tenantId, Supplier<T> work) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.of(tenantId, null, "oidc"))) {
            return transactions.execute(status -> work.get());
        }
    }
}

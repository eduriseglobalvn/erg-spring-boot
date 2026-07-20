package com.erg.ergspringboot.config.persistence;

import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.platform.security.TenantGucApplier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link JpaTransactionManager} that pushes the request-scoped {@link TenantContext} into PostgreSQL
 * session GUCs at the start of every transaction (spec §9 RLS enforcement).
 *
 * <p>This is the single choke point that activates row-level security: {@link TenantGucApplier} runs
 * {@code set_config('app.tenant_id', ..., true)} on the transaction's own connection immediately after
 * {@code doBegin}, so the value is transaction-local and every query in the transaction is hard-scoped to
 * the request's tenant. When no request context is present (Flyway, admin tooling, background jobs) the
 * GUC is left unset and the RLS policies fall back to permissive — no lockout of maintenance paths.
 *
 * <p>Deterministic by design: no AOP, no ordering ambiguity. The GUC is applied exactly once per
 * transaction, before any repository query runs inside it. A request-bound transaction is fail-closed:
 * if context installation cannot be proven, no tenant-scoped query is allowed to run.
 */
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareJpaTransactionManager.class);

    public TenantAwareJpaTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        applyTenantGuc();
    }

    private void applyTenantGuc() {
        TenantContext context = TenantContext.current();
        if (context == null) {
            return;
        }
        EntityManagerFactory emf = getEntityManagerFactory();
        if (emf == null) {
            return;
        }
        Object resource = TransactionSynchronizationManager.getResource(emf);
        if (!(resource instanceof EntityManagerHolder holder)) {
            return;
        }
        EntityManager entityManager = holder.getEntityManager();
        if (entityManager == null) {
            return;
        }
        try {
            entityManager.unwrap(Session.class)
                    .doWork(connection -> TenantGucApplier.apply(connection, context));
        } catch (RuntimeException ex) {
            log.error("Failed to apply tenant GUC for {}; aborting transaction", context.tenantId(), ex);
            throw new IllegalStateException("Unable to install tenant transaction context", ex);
        }
    }
}

package com.erg.ergspringboot.config.persistence;

import javax.sql.DataSource;
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

    /**
     * The EMF hands over its TenantGucDataSource wrapper, but JdbcTemplate uses the pool itself. Bind the
     * transaction's connection under the pool, as Spring does for TransactionAwareDataSourceProxy, so JDBC writes
     * join the JPA transaction (and its tenant) instead of running uncommitted on a side connection.
     */
    @Override
    public void setDataSource(DataSource dataSource) {
        super.setDataSource(dataSource instanceof TenantGucDataSource wrapper ? wrapper.getTargetDataSource() : dataSource);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        // The checkout inside doBegin skips the per-connection session apply; applyTenantGuc does both in one round trip.
        TenantGucDataSource.beginTransactionCheckout(true);
        try {
            super.doBegin(transaction, definition);
        } finally {
            TenantGucDataSource.beginTransactionCheckout(false);
        }
        applyTenantGuc();
    }

    private void applyTenantGuc() {
        TenantContext context = TenantContext.current();
        EntityManagerFactory emf = getEntityManagerFactory();
        Object resource = emf == null ? null : TransactionSynchronizationManager.getResource(emf);
        EntityManager entityManager = resource instanceof EntityManagerHolder holder ? holder.getEntityManager() : null;
        if (entityManager == null) {
            // The checkout skipped its own apply, so a connection without GUCs must never run a query: fail closed.
            throw new IllegalStateException("Unable to install tenant transaction context: no EntityManager bound");
        }
        try {
            entityManager.unwrap(Session.class)
                    .doWork(connection -> TenantGucApplier.applyTransaction(connection, context));
        } catch (RuntimeException ex) {
            log.error("Failed to apply tenant GUC for {}; aborting transaction", context == null ? null : context.tenantId(), ex);
            throw new IllegalStateException("Unable to install tenant transaction context", ex);
        }
    }
}

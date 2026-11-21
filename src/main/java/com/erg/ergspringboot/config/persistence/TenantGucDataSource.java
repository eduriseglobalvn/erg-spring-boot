package com.erg.ergspringboot.config.persistence;

import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.platform.security.TenantGucApplier;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.core.InfrastructureProxy;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Data source handed to JPA: every pooled connection gets the request's tenant GUCs when it is borrowed.
 * {@link TenantAwareJpaTransactionManager} only covers queries inside a Spring transaction; read paths without one
 * (Hibernate's own BEGIN/ROLLBACK) otherwise run with no app.tenant_id and RLS hides every row from the runtime role.
 *
 * <p>It is an {@link InfrastructureProxy} so a JdbcTemplate built on it joins the connection a Spring transaction bound
 * under the raw pool, and otherwise gets the same apply/clear on checkout as JPA.
 */
final class TenantGucDataSource extends DelegatingDataSource implements InfrastructureProxy {

    /** Set by {@link TenantAwareJpaTransactionManager#doBegin} while it checks out a connection it configures itself. */
    private static final ThreadLocal<Boolean> TRANSACTION_BEGIN = new ThreadLocal<>();

    TenantGucDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return scoped(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return scoped(super.getConnection(username, password));
    }

    static void beginTransactionCheckout(boolean active) {
        if (active) {
            TRANSACTION_BEGIN.set(Boolean.TRUE);
        } else {
            TRANSACTION_BEGIN.remove();
        }
    }

    @Override
    public Object getWrappedObject() {
        return getTargetDataSource();
    }

    private static Connection scoped(Connection connection) throws SQLException {
        if (TRANSACTION_BEGIN.get() != null) {
            // doBegin sets session and transaction values in one round trip right after this checkout (and fails the
            // transaction if it cannot), so the extra set_config + COMMIT here would be redundant.
            return connection;
        }
        try {
            TenantGucApplier.applySession(connection, TenantContext.current());
            return connection;
        } catch (SQLException | RuntimeException ex) {
            connection.close();
            throw ex;
        }
    }
}

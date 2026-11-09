package com.erg.ergspringboot.config.persistence;

import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.platform.security.TenantGucApplier;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Data source handed to JPA: every pooled connection gets the request's tenant GUCs when it is borrowed.
 * {@link TenantAwareJpaTransactionManager} only covers queries inside a Spring transaction; read paths without one
 * (Hibernate's own BEGIN/ROLLBACK) otherwise run with no app.tenant_id and RLS hides every row from the runtime role.
 */
final class TenantGucDataSource extends DelegatingDataSource {

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

    private static Connection scoped(Connection connection) throws SQLException {
        try {
            TenantGucApplier.applySession(connection, TenantContext.current());
            return connection;
        } catch (SQLException | RuntimeException ex) {
            connection.close();
            throw ex;
        }
    }
}

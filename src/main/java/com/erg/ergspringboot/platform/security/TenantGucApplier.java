package com.erg.ergspringboot.platform.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Applies the request-scoped {@link TenantContext} onto a PostgreSQL connection as session GUCs (RLS).
 *
 * <p>{@link #apply} uses {@code set_config(key, value, true)}: transaction-local, reverting at commit/rollback to the
 * connection's session value. That value belongs to whichever borrower used the connection last, so a connection is
 * only safe for a query when its borrower also set or cleared the session value: {@link #applySession} at checkout,
 * or {@link #applyTransaction} (both variants in one round trip) at the start of a Spring transaction.
 * {@code current_setting(key, true)} reads a value back without erroring when unset.
 *
 * <p>The application runtime must use a dedicated non-owner database role; Flyway uses a separate
 * migration-owner credential. Legacy-table policy rollout remains incremental and explicitly gated.
 */
public final class TenantGucApplier {

    public static final String TENANT_GUC = "app.tenant_id";
    public static final String USER_GUC = "app.user_id";
    public static final String PORTAL_GUC = "app.portal";
    public static final String MAINTENANCE_GUC = "app.maintenance_mode";

    private static final String SET_ALL_SQL = "select "
            + "set_config(?, ?, false), set_config(?, ?, false), set_config(?, ?, false), set_config(?, ?, false), "
            + "set_config(?, ?, true), set_config(?, ?, true), set_config(?, ?, true), set_config(?, ?, true)";

    private TenantGucApplier() {
    }

    /** Applies the current thread's context, if any, onto the given connection. No-op when unset. */
    public static void applyCurrent(Connection connection) throws SQLException {
        apply(connection, TenantContext.current());
    }

    public static void apply(Connection connection, TenantContext context) throws SQLException {
        if (context == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "select set_config(?, ?, true), set_config(?, ?, true), set_config(?, ?, true), set_config(?, ?, true)")) {
            statement.setString(1, TENANT_GUC);
            statement.setString(2, normalize(context.tenantId()));
            statement.setString(3, USER_GUC);
            statement.setString(4, normalize(context.userId()));
            statement.setString(5, PORTAL_GUC);
            statement.setString(6, normalize(context.portal()));
            statement.setString(7, MAINTENANCE_GUC);
            statement.setString(8, Boolean.toString(context.isMaintenance()));
            statement.execute();
        }
    }

    /**
     * Transaction start: sets the session-level and the transaction-local values in one statement. The transaction-local
     * value is what the transaction's queries see (also when no context exists: it clears); the session-level value is
     * what remains on the connection after commit. No context clears both, as on a fresh connection.
     */
    public static void applyTransaction(Connection connection, TenantContext context) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SET_ALL_SQL)) {
            bind(statement, bind(statement, 1, context), context);
            statement.execute();
        }
    }

    /**
     * Session-level variant for a connection just taken from the pool: sets the context, or clears what a previous
     * borrower left (no context = unset, as on a fresh connection), so queries Hibernate runs outside a Spring
     * transaction are scoped too. Commits when auto-commit is off, so the setting outlives the implicit transaction.
     */
    public static void applySession(Connection connection, TenantContext context) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select set_config(?, ?, false), set_config(?, ?, false), set_config(?, ?, false), set_config(?, ?, false)")) {
            bind(statement, 1, context);
            statement.execute();
        }
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
    }

    private static int bind(PreparedStatement statement, int index, TenantContext context) throws SQLException {
        statement.setString(index, TENANT_GUC);
        statement.setString(index + 1, context == null ? "" : normalize(context.tenantId()));
        statement.setString(index + 2, USER_GUC);
        statement.setString(index + 3, context == null ? "" : normalize(context.userId()));
        statement.setString(index + 4, PORTAL_GUC);
        statement.setString(index + 5, context == null ? "" : normalize(context.portal()));
        statement.setString(index + 6, MAINTENANCE_GUC);
        statement.setString(index + 7, context == null ? "" : Boolean.toString(context.isMaintenance()));
        return index + 8;
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}

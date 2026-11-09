package com.erg.ergspringboot.platform.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Applies the request-scoped {@link TenantContext} onto a PostgreSQL connection as transaction-local
 * session GUCs (spec §1, Phase 0.4 groundwork for RLS).
 *
 * <p>Uses {@code set_config(key, value, true)} where the trailing {@code true} scopes the setting to the
 * current transaction, so the value is automatically reset when the transaction ends and never leaks to
 * the next borrower of a pooled connection. {@code current_setting(key, true)} then reads it back without
 * erroring when unset. Strict quiz-domain RLS policies read {@code app.tenant_id} etc. this way.
 *
 * <p>The application runtime must use a dedicated non-owner database role; Flyway uses a separate
 * migration-owner credential. Legacy-table policy rollout remains incremental and explicitly gated.
 */
public final class TenantGucApplier {

    public static final String TENANT_GUC = "app.tenant_id";
    public static final String USER_GUC = "app.user_id";
    public static final String PORTAL_GUC = "app.portal";
    public static final String MAINTENANCE_GUC = "app.maintenance_mode";

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
     * Session-level variant for a connection just taken from the pool: sets the context, or clears what a previous
     * borrower left (no context = unset, as on a fresh connection), so queries Hibernate runs outside a Spring
     * transaction are scoped too. Commits when auto-commit is off, so the setting outlives the implicit transaction.
     */
    public static void applySession(Connection connection, TenantContext context) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select set_config(?, ?, false), set_config(?, ?, false), set_config(?, ?, false), set_config(?, ?, false)")) {
            statement.setString(1, TENANT_GUC);
            statement.setString(2, context == null ? "" : normalize(context.tenantId()));
            statement.setString(3, USER_GUC);
            statement.setString(4, context == null ? "" : normalize(context.userId()));
            statement.setString(5, PORTAL_GUC);
            statement.setString(6, context == null ? "" : normalize(context.portal()));
            statement.setString(7, MAINTENANCE_GUC);
            statement.setString(8, context == null ? "" : Boolean.toString(context.isMaintenance()));
            statement.execute();
        }
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    private static void setLocal(Connection connection, String key, String value) throws SQLException {
        // set_config(key, value, true) — transaction-local, auto-reset at commit/rollback.
        try (PreparedStatement statement = connection.prepareStatement("select set_config(?, ?, true)")) {
            statement.setString(1, key);
            statement.setString(2, value == null ? "" : value);
            statement.execute();
        }
    }
}

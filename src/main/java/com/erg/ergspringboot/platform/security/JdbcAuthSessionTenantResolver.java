package com.erg.ergspringboot.platform.security;

import java.sql.PreparedStatement;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Calls the narrow SECURITY DEFINER lookup used before tenant RLS is applied. */
@Component
public final class JdbcAuthSessionTenantResolver implements AuthSessionTenantResolver {

    private static final String RESOLVE_SQL = "select public.erg_resolve_session_tenant(?)";

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuthSessionTenantResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<String> resolve(String accessTokenHash) {
        if (accessTokenHash == null || accessTokenHash.isBlank()) {
            return Optional.empty();
        }
        String tenantId = jdbcTemplate.query(
                RESOLVE_SQL,
                (PreparedStatement statement) -> statement.setString(1, accessTokenHash.trim()),
                resultSet -> resultSet.next() ? resultSet.getString(1) : null
        );
        return Optional.ofNullable(SecurityUtils.trimToNull(tenantId));
    }
}

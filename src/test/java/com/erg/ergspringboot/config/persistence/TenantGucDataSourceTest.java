package com.erg.ergspringboot.config.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.platform.security.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantGucDataSourceTest {

    private final DataSource target = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void borrowedConnectionCarriesTheRequestTenantAndCommitsIt() throws SQLException {
        when(target.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(connection.getAutoCommit()).thenReturn(false);
        TenantContext.set(TenantContext.of("erg", "usr-1", "lms"));

        new TenantGucDataSource(target).getConnection();

        verify(statement).setString(2, "erg");
        verify(statement).setString(4, "usr-1");
        verify(statement).setString(6, "lms");
        verify(statement).setString(8, "false");
        verify(statement).execute();
        verify(connection).commit();
    }

    @Test
    void withoutContextClearsWhatTheLastBorrowerLeft() throws SQLException {
        when(target.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(connection.getAutoCommit()).thenReturn(true);

        new TenantGucDataSource(target).getConnection();

        verify(statement).setString(2, "");
        verify(statement).setString(8, "");
        verify(connection, never()).commit();
    }

    @Test
    void closesTheConnectionWhenTheTenantCannotBeSet() throws SQLException {
        when(target.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("down"));

        assertThatThrownBy(() -> new TenantGucDataSource(target).getConnection()).isInstanceOf(SQLException.class);
        verify(connection).close();
    }
}

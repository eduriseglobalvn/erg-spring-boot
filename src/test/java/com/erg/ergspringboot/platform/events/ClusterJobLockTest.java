package com.erg.ergspringboot.platform.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class ClusterJobLockTest {

    /**
     * The pool runs with auto-commit off, so the try-lock query opens a transaction. Left open for the
     * whole job, Postgres' idle_in_transaction_session_timeout (30s) would kill the lock session mid-job.
     */
    @Test
    void lockConnectionIsNotLeftIdleInTransactionWhileTheJobRuns() throws Exception {
        List<String> events = new ArrayList<>();
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(false);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getBoolean(1)).thenReturn(true);
        doAnswer(invocation -> events.add("commit")).when(connection).commit();

        boolean ran = new ClusterJobLock(dataSource).runExclusively("job", () -> events.add("task"));

        assertThat(ran).isTrue();
        assertThat(events).first().isEqualTo("commit");
        assertThat(events).containsSubsequence("commit", "task");
    }
}

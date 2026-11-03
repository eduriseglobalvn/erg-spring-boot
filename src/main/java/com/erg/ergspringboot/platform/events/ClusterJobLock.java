package com.erg.ergspringboot.platform.events;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cluster-wide "only one instance runs this scheduled job" guard backed by a PostgreSQL
 * session advisory lock. The lock lives on a dedicated pooled connection for the duration of
 * the job, so a crashed instance releases it when its connection closes. Callers that do not
 * get the lock simply skip the run; nothing is queued.
 */
@Component
public class ClusterJobLock {

    private static final Logger log = LoggerFactory.getLogger(ClusterJobLock.class);
    /** High word "JOBS" keeps job keys out of other advisory-lock key spaces. */
    private static final long NAMESPACE = 0x4A4F4253L << 32;

    private final DataSource dataSource;

    public ClusterJobLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Runs {@code task} if this instance wins the lock for {@code jobName}; returns whether it ran. */
    public boolean runExclusively(String jobName, Runnable task) {
        return callExclusively(jobName, () -> {
            task.run();
            return Boolean.TRUE;
        }) != null;
    }

    /** Like {@link #runExclusively} but returns the task result, or {@code null} when skipped. */
    public <T> T callExclusively(String jobName, Supplier<T> task) {
        long key = lockKey(jobName);
        try (Connection connection = dataSource.getConnection()) {
            if (!advisory(connection, "select pg_try_advisory_lock(?)", key)) {
                log.debug("Skipping scheduled job {}: another instance holds its lock", jobName);
                return null;
            }
            try {
                return task.get();
            } finally {
                advisory(connection, "select pg_advisory_unlock(?)", key);
                if (!connection.getAutoCommit()) {
                    connection.commit();
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot use cluster job lock for " + jobName, exception);
        }
    }

    public static long lockKey(String jobName) {
        return NAMESPACE | (jobName.hashCode() & 0xFFFFFFFFL);
    }

    private static boolean advisory(Connection connection, String sql, long key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }
}

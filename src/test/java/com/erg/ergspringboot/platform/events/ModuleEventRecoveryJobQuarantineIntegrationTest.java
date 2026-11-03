package com.erg.ergspringboot.platform.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** N7/C16: poison publications are quarantined after N attempts and only one instance recovers at a time. */
@Testcontainers(disabledWithoutDocker = true)
class ModuleEventRecoveryJobQuarantineIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("erg_events")
            .withUsername("erg_owner")
            .withPassword("erg_owner");

    private static DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/postgres").load().migrate();
    }

    @BeforeEach
    void clean() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("delete from event_publication");
        jdbc.update("delete from event_publication_quarantine");
    }

    @Test
    void exhaustedFailedPublicationsAreQuarantinedAndCounted() {
        UUID poison = insertPublication("FAILED", 10);
        UUID retryable = insertPublication("FAILED", 2);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FailedEventPublications failed = mock(FailedEventPublications.class);
        ModuleEventRecoveryJob job = job(failed, registry);

        job.recover();

        assertThat(jdbc.queryForList("select id from event_publication", UUID.class)).containsExactly(retryable);
        assertThat(jdbc.queryForList("select id from event_publication_quarantine", UUID.class)).containsExactly(poison);
        assertThat(registry.counter(ModuleEventRecoveryJob.QUARANTINED_METRIC).count()).isEqualTo(1.0);
        org.mockito.ArgumentCaptor<ResubmissionOptions> options = org.mockito.ArgumentCaptor.forClass(ResubmissionOptions.class);
        verify(failed).resubmit(options.capture());
        assertThat(options.getValue().getFilter()).isNotNull();
    }

    @Test
    void recoverySkipsWhileAnotherInstanceHoldsTheClusterLock() throws Exception {
        insertPublication("FAILED", 10);
        FailedEventPublications failed = mock(FailedEventPublications.class);
        ModuleEventRecoveryJob job = job(failed, new SimpleMeterRegistry());

        try (Connection other = dataSource.getConnection();
             PreparedStatement lock = other.prepareStatement("select pg_advisory_lock(?)")) {
            lock.setLong(1, ClusterJobLock.lockKey(ModuleEventRecoveryJob.JOB_NAME));
            lock.execute();

            job.recover();

            verify(failed, never()).resubmit(any());
            assertThat(jdbc.queryForObject("select count(*) from event_publication_quarantine", Integer.class)).isZero();
        }
    }

    private ModuleEventRecoveryJob job(FailedEventPublications failed, SimpleMeterRegistry registry) {
        return new ModuleEventRecoveryJob(failed, mock(IncompleteEventPublications.class), 100, 16,
                Duration.ofSeconds(30), 5, 500, jdbc, new ClusterJobLock(dataSource), registry);
    }

    private UUID insertPublication(String status, int attempts) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into event_publication (id, listener_id, event_type, serialized_event, publication_date,
                    status, completion_attempts)
                values (?, 'listener', 'com.example.Event', '{}', now() - interval '1 hour', ?, ?)
                """, id, status, attempts);
        return id;
    }
}

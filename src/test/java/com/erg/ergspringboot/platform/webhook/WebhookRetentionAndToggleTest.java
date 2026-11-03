package com.erg.ergspringboot.platform.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.erg.ergspringboot.exception.ResourceNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/** C13: retention for webhook ingress, a disable flag and bounded rejection metrics. */
@Testcontainers(disabledWithoutDocker = true)
class WebhookRetentionAndToggleTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("erg_webhook")
            .withUsername("erg_owner")
            .withPassword("erg_owner");

    @Test
    void retentionDeletesExpiredReceiptsAndOldInboxRowsOnly() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/postgres").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into webhook_receipts values ('telegram','old',now() - interval '2 days',now() - interval '1 day')");
        jdbc.update("insert into webhook_receipts values ('telegram','live',now(),now() + interval '1 day')");
        jdbc.update("insert into webhook_inbox(provider, receipt_key, payload, status, received_at) "
                + "values ('telegram','old','{}','pending', now() - interval '40 days')");
        jdbc.update("insert into webhook_inbox(provider, receipt_key, payload, status, received_at) "
                + "values ('telegram','new','{}','pending', now())");
        WebhookProperties properties = new WebhookProperties();
        properties.setInboxRetention(Duration.ofDays(30));
        properties.setRetentionBatchSize(1);

        int deleted = new WebhookRetentionJob(jdbc, properties, (com.erg.ergspringboot.platform.events.ClusterJobLock) null).purge();

        assertThat(deleted).isEqualTo(2);
        assertThat(jdbc.queryForList("select receipt_key from webhook_receipts", String.class)).containsExactly("live");
        assertThat(jdbc.queryForList("select receipt_key from webhook_inbox", String.class)).containsExactly("new");
    }

    @Test
    void disabledRoutesAcceptNothing() {
        WebhookReplayGuard guard = mock(WebhookReplayGuard.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WebhookProperties properties = new WebhookProperties();
        properties.setEnabled(false);
        WebhookInboxService inbox = new WebhookInboxService(guard, jdbc, new ObjectMapper(), properties,
                new StaticListableBeanFactory().getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class));

        assertThatThrownBy(() -> inbox.accept("telegram", "1", new ObjectMapper().createObjectNode()))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(guard, jdbc);
    }

    @Test
    void failedVerificationIsCountedWithABoundedReason() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("registry", registry);
        WebhookInboxService inbox = new WebhookInboxService(mock(WebhookReplayGuard.class), mock(JdbcTemplate.class),
                new ObjectMapper(), new WebhookProperties(),
                beans.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class));
        WebhookRejectionMetricsFilter filter = new WebhookRejectionMetricsFilter(inbox);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhooks/discord");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(401);

        filter.doFilter(request, response, new MockFilterChain());
        inbox.rejected("discord", "made-up-reason-" + System.nanoTime());

        assertThat(registry.counter("webhook.rejected.verification", "provider", "discord").count()).isEqualTo(1.0);
        assertThat(registry.counter("webhook.rejected.other", "provider", "discord").count()).isEqualTo(1.0);
        assertThat(WebhookRejectionMetricsFilter.reason(202)).isNull();
    }
}

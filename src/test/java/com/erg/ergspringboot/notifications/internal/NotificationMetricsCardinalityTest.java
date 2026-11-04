package com.erg.ergspringboot.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** C12: free-form event types, portals and provider codes must not create unbounded meter series. */
class NotificationMetricsCardinalityTest {

    @Test
    void unknownTagValuesCollapseIntoOther() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("registry", registry);
        NotificationMetrics metrics = new NotificationMetrics(beans.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class),
                mock(NotificationOutboxRepository.class), mock(NotificationDeviceRegistrationRepository.class));

        for (int i = 0; i < 50; i++) {
            metrics.created("RANDOM_" + i, "portal-" + i, 1, 1);
            metrics.sendResult("kind-" + i, "provider says " + i, false, 5);
        }
        metrics.created("schedule_published", "LMS", 1, 0);
        metrics.sendResult("registration_token", "HTTP_503", false, 5);

        assertThat(registry.find("notifications.created.count").counters()).hasSize(2);
        assertThat(registry.find("notifications.created.count").tag("eventType", "other").tag("portal", "other").counter())
                .isNotNull();
        assertThat(registry.find("notifications.created.count").tag("eventType", "SCHEDULE_PUBLISHED").tag("portal", "lms")
                .counter()).isNotNull();
        assertThat(registry.find("notifications.send.count").counters()).hasSize(2);
        assertThat(NotificationMetrics.errorCodeTag("HTTP_503")).isEqualTo("HTTP_503");
        assertThat(NotificationMetrics.errorCodeTag("unregistered")).isEqualTo("UNREGISTERED");
        assertThat(NotificationMetrics.errorCodeTag("<html>boom</html>")).isEqualTo("other");
    }
}

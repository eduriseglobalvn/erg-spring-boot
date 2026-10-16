package com.erg.ergspringboot.notifications.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.notifications.push.FirebasePushSender;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class FirebaseNotificationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FirebaseNotificationConfiguration.class)
            .withBean(NotificationPushProperties.class, NotificationPushProperties::new)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void disabledPushDoesNotAllocateAnHttpClient() {
        contextRunner
                .withPropertyValues("erg.notifications.push.enabled=false")
                .run(context -> {
                    assertThat(context.getBeansOfType(HttpClient.class)).isEmpty();
                    assertThat(context.getBeansOfType(FirebasePushSender.class)).hasSize(1);
                });
    }
}

package com.erg.ergspringboot.notifications.config;

import com.erg.ergspringboot.notifications.push.AccessTokenProvider;
import com.erg.ergspringboot.notifications.push.FirebaseHttpV1PushSender;
import com.erg.ergspringboot.notifications.push.FirebasePushSender;
import com.erg.ergspringboot.notifications.push.ServiceAccountAccessTokenProvider;
import com.erg.ergspringboot.notifications.push.StaticAccessTokenProvider;
import java.net.http.HttpClient;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableScheduling
@ImportRuntimeHints(FirebaseNotificationRuntimeHints.class)
public class FirebaseNotificationConfiguration {

    @Bean
    HttpClient firebaseHttpClient(NotificationPushProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    @Bean
    AccessTokenProvider firebaseAccessTokenProvider(
            NotificationPushProperties properties,
            HttpClient firebaseHttpClient,
            ObjectMapper objectMapper
    ) {
        String staticToken = trimToNull(properties.getCredentials().getStaticAccessToken());
        if (staticToken != null) {
            return new StaticAccessTokenProvider(staticToken);
        }
        String serviceAccountPath = firstText(
                properties.getCredentials().getServiceAccountPath(),
                System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        );
        if (serviceAccountPath != null) {
            return new ServiceAccountAccessTokenProvider(serviceAccountPath, firebaseHttpClient, objectMapper);
        }
        return () -> Optional.empty();
    }

    @Bean
    FirebasePushSender firebasePushSender(
            NotificationPushProperties properties,
            HttpClient firebaseHttpClient,
            AccessTokenProvider accessTokenProvider,
            ObjectMapper objectMapper
    ) {
        return new FirebaseHttpV1PushSender(properties, firebaseHttpClient, accessTokenProvider, objectMapper);
    }

    private String firstText(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

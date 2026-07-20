package com.erg.ergspringboot.notifications.push;

import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class FirebaseHttpV1PushSender implements FirebasePushSender {

    private final NotificationPushProperties properties;
    private final HttpClient httpClient;
    private final AccessTokenProvider accessTokenProvider;
    private final ObjectMapper objectMapper;

    public FirebaseHttpV1PushSender(
            NotificationPushProperties properties,
            HttpClient httpClient,
            AccessTokenProvider accessTokenProvider,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.accessTokenProvider = accessTokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public SendResult sendToRegistrationToken(FirebasePushPayload payload, String registrationToken) {
        return send(payload, "token", registrationToken);
    }

    @Override
    public SendResult sendToFid(FirebasePushPayload payload, String firebaseInstallationId) {
        return SendResult.failure(
                false,
                false,
                0,
                "FID_TARGET_UNSUPPORTED",
                "HTTP v1 FID targeting is not enabled; send registrationToken compatibility from the client.",
                null,
                0
        );
    }

    @Override
    public SendResult sendToTopic(FirebasePushPayload payload, String topicName) {
        return send(payload, "topic", topicName);
    }

    private SendResult send(FirebasePushPayload payload, String targetField, String targetValue) {
        Instant startedAt = Instant.now();
        if (!properties.isEnabled()) {
            return failure(startedAt, false, false, 0, "PUSH_DISABLED", "Firebase push is disabled.", null);
        }
        if (isBlank(properties.getProjectId())) {
            return failure(startedAt, false, false, 0, "FIREBASE_PROJECT_MISSING", "FIREBASE_PROJECT_ID is not configured.", null);
        }
        if (isBlank(targetValue)) {
            return failure(startedAt, false, false, 0, "TARGET_MISSING", "Firebase target is missing.", null);
        }
        Optional<String> accessToken = accessTokenProvider.accessToken();
        if (accessToken.isEmpty()) {
            return failure(startedAt, true, false, 0, "ACCESS_TOKEN_MISSING", "Firebase access token is unavailable.", null);
        }

        try {
            String body = objectMapper.writeValueAsString(requestBody(payload, targetField, targetValue));
            if (body.getBytes(StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes() + 2048) {
                return failure(startedAt, false, false, 0, "PAYLOAD_TOO_LARGE", "Firebase payload is too large.", null);
            }
            HttpRequest request = HttpRequest.newBuilder(sendUri())
                    .timeout(properties.getReadTimeout())
                    .header("Authorization", "Bearer " + accessToken.get())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return SendResult.success(response.statusCode(), providerMessageId(response.body()), durationMs);
            }
            String errorCode = firebaseErrorCode(response.body(), "HTTP_" + response.statusCode());
            boolean invalidTarget = isInvalidTarget(errorCode);
            boolean retryable = !invalidTarget && isRetryable(response.statusCode());
            return SendResult.failure(
                    retryable,
                    invalidTarget,
                    response.statusCode(),
                    errorCode,
                    sample(response.body()),
                    retryAfter(response),
                    durationMs
            );
        } catch (IOException exception) {
            return failure(startedAt, true, false, 0, "IO_ERROR", exception.getMessage(), null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(startedAt, true, false, 0, "INTERRUPTED", "Firebase send interrupted.", null);
        }
    }

    private Map<String, Object> requestBody(FirebasePushPayload payload, String targetField, String targetValue) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put(targetField, targetValue);
        if (!isBlank(payload.title()) || !isBlank(payload.body())) {
            Map<String, String> notification = new LinkedHashMap<>();
            if (!isBlank(payload.title())) {
                notification.put("title", payload.title());
            }
            if (!isBlank(payload.body())) {
                notification.put("body", payload.body());
            }
            message.put("notification", notification);
        }
        if (payload.data() != null && !payload.data().isEmpty()) {
            message.put("data", payload.data());
        }
        if (!isBlank(payload.actionUrl())) {
            message.put("webpush", Map.of("fcm_options", Map.of("link", payload.actionUrl())));
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("message", message);
        return request;
    }

    private URI sendUri() {
        String endpoint = properties.getEndpoint();
        String normalizedEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return URI.create(normalizedEndpoint + "/v1/projects/" + properties.getProjectId().trim() + "/messages:send");
    }

    private String providerMessageId(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode name = root.get("name");
            return name == null || name.isNull() ? null : name.asText();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String firebaseErrorCode(String responseBody, String fallback) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.get("error");
            if (error == null || error.isNull()) {
                return fallback;
            }
            JsonNode status = error.get("status");
            if (status != null && !status.isNull()) {
                return status.asText();
            }
            JsonNode code = error.get("code");
            return code == null || code.isNull() ? fallback : "HTTP_" + code.asText();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private boolean isInvalidTarget(String errorCode) {
        if (errorCode == null) {
            return false;
        }
        String normalized = errorCode.trim().toUpperCase();
        return normalized.contains("UNREGISTERED")
                || normalized.contains("NOT_FOUND")
                || normalized.contains("INVALID_ARGUMENT");
    }

    private Duration retryAfter(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After")
                .flatMap(this::parseRetryAfter)
                .orElse(null);
    }

    private Optional<Duration> parseRetryAfter(String value) {
        try {
            return Optional.of(Duration.ofSeconds(Long.parseLong(value.trim())));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private SendResult failure(
            Instant startedAt,
            boolean retryable,
            boolean invalidTarget,
            int httpStatus,
            String errorCode,
            String errorMessage,
            Duration retryAfter
    ) {
        return SendResult.failure(
                retryable,
                invalidTarget,
                httpStatus,
                errorCode,
                errorMessage,
                retryAfter,
                Duration.between(startedAt, Instant.now()).toMillis()
        );
    }

    private String sample(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

package com.erg.ergspringboot.notifications.push;

import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseHttpV1PushSenderTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsHttpV1RegistrationTokenPayload() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server = server(exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"name\":\"projects/erg-test/messages/1\"}", null);
        });

        FirebaseHttpV1PushSender sender = sender(port());
        SendResult result = sender.sendToRegistrationToken(
                new FirebasePushPayload(
                        "Bai tap moi",
                        "Ban co bai tap moi.",
                        "https://app.erg.edu.vn/lms/assignments/a1",
                        null,
                        Map.of("notificationId", "n1", "portal", "lms")
                ),
                "token-1"
        );

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("projects/erg-test/messages/1");
        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/v1/projects/erg-test/messages:send");
        assertThat(authorization.get()).isEqualTo("Bearer access-token");
        assertThat(body.get())
                .contains("\"token\":\"token-1\"")
                .contains("\"title\":\"Bai tap moi\"")
                .contains("\"notificationId\":\"n1\"")
                .contains("\"link\":\"https://app.erg.edu.vn/lms/assignments/a1\"");
    }

    @Test
    void mapsRetryAfterForQuotaErrors() throws Exception {
        server = server(exchange -> respond(
                exchange,
                429,
                "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}",
                "42"
        ));

        SendResult result = sender(port()).sendToTopic(
                new FirebasePushPayload("Title", "Body", null, null, Map.of()),
                "t_erg_portal_lms"
        );

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCode()).isEqualTo("RESOURCE_EXHAUSTED");
        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(42));
    }

    @Test
    void mapsUnregisteredTargetAsInvalidTarget() throws Exception {
        server = server(exchange -> respond(
                exchange,
                404,
                "{\"error\":{\"status\":\"UNREGISTERED\"}}",
                null
        ));

        SendResult result = sender(port()).sendToRegistrationToken(
                new FirebasePushPayload("Title", "Body", null, null, Map.of()),
                "dead-token"
        );

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.invalidTarget()).isTrue();
        assertThat(result.errorCode()).isEqualTo("UNREGISTERED");
    }

    private FirebaseHttpV1PushSender sender(int port) {
        NotificationPushProperties properties = new NotificationPushProperties();
        properties.setEnabled(true);
        properties.setProjectId("erg-test");
        properties.setEndpoint("http://127.0.0.1:" + port);
        return new FirebaseHttpV1PushSender(
                properties,
                HttpClient.newHttpClient(),
                () -> java.util.Optional.of("access-token"),
                new ObjectMapper()
        );
    }

    private HttpServer server(ExchangeHandler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception exception) {
                respond(exchange, 500, exception.getMessage(), null);
            }
        });
        httpServer.start();
        return httpServer;
    }

    private int port() {
        return server.getAddress().getPort();
    }

    private void respond(HttpExchange exchange, int status, String body, String retryAfter) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (retryAfter != null) {
            exchange.getResponseHeaders().set("Retry-After", retryAfter);
        }
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}

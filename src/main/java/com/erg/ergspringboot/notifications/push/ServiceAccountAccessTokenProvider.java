package com.erg.ergspringboot.notifications.push;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class ServiceAccountAccessTokenProvider implements AccessTokenProvider {

    private static final String FIREBASE_MESSAGING_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

    private final String serviceAccountPath;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private volatile CachedToken cachedToken;

    public ServiceAccountAccessTokenProvider(String serviceAccountPath, HttpClient httpClient, ObjectMapper objectMapper) {
        this.serviceAccountPath = serviceAccountPath;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<String> accessToken() {
        CachedToken token = cachedToken;
        if (token != null && token.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return Optional.of(token.value());
        }
        try {
            CachedToken refreshed = refreshToken();
            cachedToken = refreshed;
            return Optional.of(refreshed.value());
        } catch (IOException | InterruptedException | GeneralSecurityException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    private CachedToken refreshToken() throws IOException, InterruptedException, GeneralSecurityException {
        JsonNode serviceAccount = objectMapper.readTree(Files.readAllBytes(Path.of(serviceAccountPath)));
        String clientEmail = text(serviceAccount, "client_email");
        String privateKeyPem = text(serviceAccount, "private_key");
        String tokenUri = firstText(text(serviceAccount, "token_uri"), DEFAULT_TOKEN_URI);
        if (clientEmail == null || privateKeyPem == null) {
            throw new IOException("Firebase service account file is missing client_email or private_key.");
        }

        Instant now = Instant.now();
        String assertion = signJwt(clientEmail, privateKeyPem, tokenUri, now);
        String form = "grant_type=" + encode("urn:ietf:params:oauth:grant-type:jwt-bearer")
                + "&assertion=" + encode(assertion);
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUri))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Firebase OAuth token request failed with HTTP " + response.statusCode());
        }
        JsonNode body = objectMapper.readTree(response.body());
        String accessToken = text(body, "access_token");
        long expiresIn = longValue(body, "expires_in", 3600);
        if (accessToken == null) {
            throw new IOException("Firebase OAuth token response is missing access_token.");
        }
        return new CachedToken(accessToken, now.plusSeconds(Math.max(60, expiresIn)));
    }

    private String signJwt(String clientEmail, String privateKeyPem, String audience, Instant now)
            throws GeneralSecurityException {
        String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String claims = "{"
                + "\"iss\":\"" + json(clientEmail) + "\","
                + "\"scope\":\"" + FIREBASE_MESSAGING_SCOPE + "\","
                + "\"aud\":\"" + json(audience) + "\","
                + "\"iat\":" + now.getEpochSecond() + ","
                + "\"exp\":" + now.plusSeconds(3600).getEpochSecond()
                + "}";
        String payload = base64Url(claims.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey(privateKeyPem));
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + base64Url(signature.sign());
    }

    private PrivateKey privateKey(String privateKeyPem) throws GeneralSecurityException {
        String normalized = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private long longValue(JsonNode node, String field, long fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? fallback : value.asLong(fallback);
    }

    private String firstText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}

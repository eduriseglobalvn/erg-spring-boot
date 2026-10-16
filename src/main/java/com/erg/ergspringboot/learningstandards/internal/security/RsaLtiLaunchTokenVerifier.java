package com.erg.ergspringboot.learningstandards.internal.security;

import com.erg.ergspringboot.learningstandards.LtiLaunchTokenVerifier;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Native-friendly RS256 JWT verifier for LTI 1.3 launches. Key material is
 * supplied by a tenant-owned registration; no remote JWKS fetch occurs on the
 * request path.
 */
@Component
public final class RsaLtiLaunchTokenVerifier implements LtiLaunchTokenVerifier {
    private static final Pattern HOST = Pattern.compile("[a-z0-9.-]{1,255}");
    private final ObjectMapper objectMapper;

    public RsaLtiLaunchTokenVerifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void validateRegistration(Registration registration) {
        if (registration == null || blank(registration.issuer()) || blank(registration.clientId())
                || blank(registration.deploymentId()) || blank(registration.publicKeyPem())
                || blank(registration.allowedTargetHost())) {
            throw new IllegalArgumentException("LTI registration is incomplete");
        }
        if (!https(registration.issuer()) || !HOST.matcher(registration.allowedTargetHost().toLowerCase(Locale.ROOT)).matches()) {
            throw new IllegalArgumentException("LTI issuer/target host is not safe");
        }
        publicKey(registration.publicKeyPem());
    }

    @Override
    public VerifiedLaunch verify(String signedToken, String nonce, Registration registration) {
        validateRegistration(registration);
        if (blank(signedToken) || blank(nonce)) throw new IllegalArgumentException("LTI launch proof is missing");
        String[] parts = signedToken.split("\\.", -1);
        if (parts.length != 3) throw new IllegalArgumentException("LTI launch token is malformed");
        try {
            byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
            JsonNode header = objectMapper.readTree(headerBytes);
            JsonNode payload = objectMapper.readTree(payloadBytes);
            if (!"RS256".equals(text(header, "alg"))) throw new IllegalArgumentException("only RS256 is allowed");
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey(registration.publicKeyPem()));
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!verifier.verify(signatureBytes)) throw new IllegalArgumentException("LTI launch signature is invalid");
            if (!registration.issuer().equals(text(payload, "iss"))
                    || !audienceContains(payload.get("aud"), registration.clientId())
                    || !registration.deploymentId().equals(text(payload, "https://purl.imsglobal.org/spec/lti/claim/deployment_id"))
                    || !nonce.equals(text(payload, "nonce"))) {
                throw new IllegalArgumentException("LTI launch claims are invalid");
            }
            long now = Instant.now().getEpochSecond();
            long exp = longValue(payload.get("exp"));
            long iat = longValue(payload.get("iat"));
            if (exp <= now || iat > now + 60) throw new IllegalArgumentException("LTI launch token is outside its validity window");
            String target = text(payload, "https://purl.imsglobal.org/spec/lti/claim/target_link_uri");
            if (!allowedTarget(target, registration.allowedTargetHost())) throw new IllegalArgumentException("LTI target is not allowlisted");
            String subject = text(payload, "sub");
            if (blank(subject)) throw new IllegalArgumentException("LTI subject is missing");
            return new VerifiedLaunch(subject, target);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("LTI launch token cannot be verified", exception);
        }
    }

    private PublicKey publicKey(String pem) {
        try {
            String encoded = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(encoded);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception exception) {
            throw new IllegalArgumentException("LTI public key is invalid", exception);
        }
    }

    private boolean audienceContains(JsonNode node, String expected) {
        if (node == null || node.isNull()) return false;
        if (node.isArray()) {
            for (JsonNode value : node) if (expected.equals(value.asText())) return true;
            return false;
        }
        return expected.equals(node.asText());
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private long longValue(JsonNode node) {
        if (node == null || !node.isNumber()) throw new IllegalArgumentException("LTI time claim is invalid");
        return node.asLong();
    }

    private boolean allowedTarget(String target, String allowedHost) {
        if (blank(target)) return false;
        try {
            java.net.URI uri = java.net.URI.create(target);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && allowedHost.equalsIgnoreCase(uri.getHost())
                    && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean https(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

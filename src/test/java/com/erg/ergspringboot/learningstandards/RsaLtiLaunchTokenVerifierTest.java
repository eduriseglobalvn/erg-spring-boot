package com.erg.ergspringboot.learningstandards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.learningstandards.internal.security.RsaLtiLaunchTokenVerifier;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RsaLtiLaunchTokenVerifierTest {
    private KeyPair keyPair;
    private RsaLtiLaunchTokenVerifier verifier;
    private LtiLaunchTokenVerifier.Registration registration;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        verifier = new RsaLtiLaunchTokenVerifier(new ObjectMapper());
        registration = new LtiLaunchTokenVerifier.Registration(
                "https://issuer.example.test", "client-1", "deployment-1", publicKeyPem(), "lms.example.test");
    }

    @Test
    void verifiesSignedRs256LaunchAndAllowlistedTarget() throws Exception {
        String token = token("RS256", "https://lms.example.test/launch?resource=1", "nonce-1", "client-1");

        var launch = verifier.verify(token, "nonce-1", registration);

        assertThat(launch.subject()).isEqualTo("learner-1");
        assertThat(launch.targetLinkUri()).startsWith("https://lms.example.test/");
    }

    @Test
    void rejectsWrongNonceAudienceAlgorithmAndTargetHost() throws Exception {
        String validTarget = "https://lms.example.test/launch";
        String valid = token("RS256", validTarget, "nonce-1", "client-1");
        assertThatThrownBy(() -> verifier.verify(valid, "different", registration))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> verifier.verify(token("RS256", validTarget, "nonce-1", "other-client"), "nonce-1", registration))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> verifier.verify(token("HS256", validTarget, "nonce-1", "client-1"), "nonce-1", registration))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> verifier.verify(token("RS256", "https://evil.example.test/launch", "nonce-1", "client-1"), "nonce-1", registration))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String token(String algorithm, String target, String nonce, String audience) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> header = Map.of("alg", algorithm, "typ", "JWT");
        long now = Instant.now().getEpochSecond();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", registration.issuer());
        payload.put("aud", audience);
        payload.put("sub", "learner-1");
        payload.put("nonce", nonce);
        payload.put("exp", now + 300);
        payload.put("iat", now - 1);
        payload.put("https://purl.imsglobal.org/spec/lti/claim/deployment_id", registration.deploymentId());
        payload.put("https://purl.imsglobal.org/spec/lti/claim/target_link_uri", target);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String encodedHeader = encoder.encodeToString(mapper.writeValueAsBytes(header));
        String encodedPayload = encoder.encodeToString(mapper.writeValueAsBytes(payload));
        String signingInput = encodedHeader + "." + encodedPayload;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + encoder.encodeToString(signature.sign());
    }

    private String publicKeyPem() {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyPair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----";
    }
}

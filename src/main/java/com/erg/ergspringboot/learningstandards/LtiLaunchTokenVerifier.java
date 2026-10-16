package com.erg.ergspringboot.learningstandards;

/** Cryptographic LTI launch port; the default adapter fails closed. */
public interface LtiLaunchTokenVerifier {
    VerifiedLaunch verify(String signedToken, String nonce, Registration registration);

    default void validateRegistration(Registration registration) {
    }

    record Registration(String issuer, String clientId, String deploymentId,
                        String publicKeyPem, String allowedTargetHost) {
    }

    record VerifiedLaunch(String subject, String targetLinkUri) {
    }
}

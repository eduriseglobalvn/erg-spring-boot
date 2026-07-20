package com.erg.ergspringboot.platform.webhook;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

public final class WebhookVerificationException extends AuthenticationCredentialsNotFoundException {
    public WebhookVerificationException(String message) {
        super(message);
    }
}

package com.erg.ergspringboot.platform.webhook;

public interface WebhookSignatureVerifier {
    void verify(byte[] rawBody, String signature, String timestamp);
}

package com.erg.ergspringboot.platform.webhook;

import com.erg.ergspringboot.exception.ConflictException;

public final class WebhookReplayException extends ConflictException {
    public WebhookReplayException(String provider) { super("Replay detected for " + provider + " webhook"); }
}

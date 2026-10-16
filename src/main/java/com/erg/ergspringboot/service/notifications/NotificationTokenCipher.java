package com.erg.ergspringboot.service.notifications;

import com.erg.ergspringboot.notifications.config.NotificationPushProperties;

/**
 * @deprecated Use the notifications module's token cipher. This source-level
 * facade keeps legacy service tests and callers source-compatible while the
 * runtime bean lives inside the owning Modulith module.
 */
@Deprecated(forRemoval = false)
public class NotificationTokenCipher extends com.erg.ergspringboot.notifications.NotificationTokenCipher {

    public NotificationTokenCipher(NotificationPushProperties properties) {
        super(properties);
    }
}

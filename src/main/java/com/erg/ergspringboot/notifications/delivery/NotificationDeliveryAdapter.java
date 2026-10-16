package com.erg.ergspringboot.notifications.delivery;

import com.erg.ergspringboot.notifications.push.SendResult;
import java.util.Set;

/** Provider boundary used by the durable notification outbox. */
public interface NotificationDeliveryAdapter {

    /** Every key returned here must be handled by this adapter. */
    Set<NotificationDeliveryKey> keys();

    SendResult deliver(NotificationDeliveryRequest request);
}

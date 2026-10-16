package com.erg.ergspringboot.notifications.delivery;

import com.erg.ergspringboot.notifications.push.FirebasePushPayload;
import com.erg.ergspringboot.notifications.push.FirebasePushSender;
import com.erg.ergspringboot.notifications.push.SendResult;
import com.erg.ergspringboot.notifications.NotificationTokenCipher;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Firebase adapter for all currently persisted push target kinds. */
@Component
public class FirebaseNotificationDeliveryAdapter implements NotificationDeliveryAdapter {

    private static final Set<NotificationDeliveryKey> KEYS = Set.of(
            NotificationDeliveryKey.of("push", "registration_token", "firebase"),
            NotificationDeliveryKey.of("push", "fid", "firebase"),
            NotificationDeliveryKey.of("push", "topic", "firebase")
    );

    private final FirebasePushSender sender;
    private final NotificationTokenCipher tokenCipher;

    public FirebaseNotificationDeliveryAdapter(FirebasePushSender sender, NotificationTokenCipher tokenCipher) {
        this.sender = sender;
        this.tokenCipher = tokenCipher;
    }

    @Override
    public Set<NotificationDeliveryKey> keys() {
        return KEYS;
    }

    @Override
    public SendResult deliver(NotificationDeliveryRequest request) {
        FirebasePushPayload payload = new FirebasePushPayload(
                request.payload().title(),
                request.payload().body(),
                request.payload().actionUrl(),
                request.payload().collapseKey(),
                request.payload().data()
        );
        try {
            return switch (request.targetKind()) {
                case "registration_token" -> sender.sendToRegistrationToken(
                        payload, tokenCipher.decrypt(request.targetRef()));
                case "fid" -> sender.sendToFid(payload, request.targetRef());
                case "topic" -> sender.sendToTopic(payload, request.targetRef());
                default -> SendResult.failure(false, false, 0, "UNKNOWN_TARGET_KIND",
                        "Unsupported notification target kind.", null, 0);
            };
        } catch (RuntimeException exception) {
            return SendResult.failure(false, false, 0, "TARGET_DECRYPT_FAILED",
                    exception.getMessage(), null, 0);
        }
    }
}

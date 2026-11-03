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
                case "registration_token" -> {
                    String token;
                    try {
                        token = tokenCipher.decrypt(request.targetRef());
                    } catch (RuntimeException exception) {
                        // Only a decrypt failure is permanent for this row; never expose the cause text.
                        yield SendResult.failure(false, false, 0, "TARGET_DECRYPT_FAILED",
                                "Stored registration token could not be decrypted.", null, 0);
                    }
                    yield sender.sendToRegistrationToken(payload, token);
                }
                case "fid" -> sender.sendToFid(payload, request.targetRef());
                case "topic" -> sender.sendToTopic(payload, request.targetRef());
                default -> SendResult.failure(false, false, 0, "UNKNOWN_TARGET_KIND",
                        "Unsupported notification target kind.", null, 0);
            };
        } catch (RuntimeException exception) {
            // Transient sender/runtime faults keep the normal retry/backoff path.
            return SendResult.failure(true, false, 0, "SENDER_RUNTIME_ERROR",
                    exception.getClass().getSimpleName(), null, 0);
        }
    }
}

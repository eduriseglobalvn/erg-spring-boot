package com.erg.ergspringboot.notifications.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.notifications.push.FirebaseHttpV1PushSender;
import com.erg.ergspringboot.notifications.push.FirebasePushPayload;
import com.erg.ergspringboot.notifications.push.FirebasePushSender;
import com.erg.ergspringboot.notifications.push.SendResult;
import com.erg.ergspringboot.notifications.NotificationTokenCipher;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** C9 + N8: FID targets disable the device; only decrypt failures are permanent. */
class FirebaseDeliveryFailureClassificationTest {

    @Test
    void fidOnlyTargetIsReportedAsInvalidSoTheDeviceGetsDisabled() {
        FirebaseHttpV1PushSender sender = new FirebaseHttpV1PushSender(
                new NotificationPushProperties(), null, Optional::empty, null);

        SendResult result = sender.sendToFid(new FirebasePushPayload("t", "b", null, null, Map.of()), "fid-1");

        assertThat(result.invalidTarget()).isTrue();
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void transientSenderRuntimeErrorStaysRetryable() {
        FirebaseNotificationDeliveryAdapter adapter = adapter(new ThrowingSender());

        SendResult result = adapter.deliver(request("registration_token", "plain-token"));

        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCode()).isEqualTo("SENDER_RUNTIME_ERROR");
    }

    @Test
    void decryptFailureIsPermanentAndDoesNotLeakTheCause() {
        NotificationPushProperties properties = new NotificationPushProperties();
        properties.getCredentials().setRegistrationTokenEncryptionKey("0123456789abcdef0123456789abcdef");
        FirebaseNotificationDeliveryAdapter adapter = new FirebaseNotificationDeliveryAdapter(
                new ThrowingSender(), new NotificationTokenCipher(properties));

        SendResult result = adapter.deliver(request("registration_token", "enc:v1:not-base64-ciphertext!"));

        assertThat(result.retryable()).isFalse();
        assertThat(result.errorCode()).isEqualTo("TARGET_DECRYPT_FAILED");
        assertThat(result.errorMessage()).doesNotContain("enc:v1");
    }

    private FirebaseNotificationDeliveryAdapter adapter(FirebasePushSender sender) {
        return new FirebaseNotificationDeliveryAdapter(sender, new NotificationTokenCipher(new NotificationPushProperties()));
    }

    private NotificationDeliveryRequest request(String targetKind, String targetRef) {
        return new NotificationDeliveryRequest("tenant", "notification", "push", targetKind, "firebase", targetRef,
                new NotificationDeliveryPayload("title", "body", null, null, Map.of()));
    }

    private static final class ThrowingSender implements FirebasePushSender {
        @Override
        public SendResult sendToRegistrationToken(FirebasePushPayload payload, String token) {
            throw new IllegalArgumentException("serializer blew up");
        }

        @Override
        public SendResult sendToFid(FirebasePushPayload payload, String fid) {
            throw new IllegalArgumentException("serializer blew up");
        }

        @Override
        public SendResult sendToTopic(FirebasePushPayload payload, String topic) {
            throw new IllegalArgumentException("serializer blew up");
        }
    }
}

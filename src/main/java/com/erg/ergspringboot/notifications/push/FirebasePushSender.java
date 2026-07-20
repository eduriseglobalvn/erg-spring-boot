package com.erg.ergspringboot.notifications.push;

public interface FirebasePushSender {

    SendResult sendToRegistrationToken(FirebasePushPayload payload, String registrationToken);

    SendResult sendToFid(FirebasePushPayload payload, String firebaseInstallationId);

    SendResult sendToTopic(FirebasePushPayload payload, String topicName);
}

package com.erg.ergspringboot.notifications.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "erg.notifications.push")
public class NotificationPushProperties {

    public static final String PROVIDER_FCM = "fcm";
    public static final String PROVIDER_WEB_PUSH = "webpush";

    private boolean enabled = false;
    /** fcm | webpush; blank picks fcm when FCM credentials are configured, otherwise webpush. */
    private String provider = "";
    private String senderMode = "http-v1";
    private boolean nativeImageStrict = true;
    private String projectId = "";
    private String endpoint = "https://fcm.googleapis.com";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(10);
    private Duration defaultTtl = Duration.ofHours(24);
    private Duration staleRegistrationAge = Duration.ofDays(30);
    private int maxPayloadBytes = 4096;
    private final Credentials credentials = new Credentials();
    private final Worker worker = new Worker();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    /** The push channel devices register on and the worker sends through. */
    public String resolvedProvider() {
        String explicit = provider == null ? "" : provider.trim().toLowerCase(java.util.Locale.ROOT);
        if (PROVIDER_FCM.equals(explicit) || PROVIDER_WEB_PUSH.equals(explicit)) {
            return explicit;
        }
        return isBlank(credentials.staticAccessToken) && isBlank(credentials.serviceAccountPath)
                ? PROVIDER_WEB_PUSH : PROVIDER_FCM;
    }

    /** FCM can send: a project id plus a static token or a readable credentials file (only its path is checked). */
    public boolean fcmReady() {
        if (isBlank(projectId)) {
            return false;
        }
        if (!isBlank(credentials.staticAccessToken)) {
            return true;
        }
        if (isBlank(credentials.serviceAccountPath)) {
            return false;
        }
        try {
            return java.nio.file.Files.isReadable(java.nio.file.Path.of(credentials.serviceAccountPath.trim()));
        } catch (java.nio.file.InvalidPathException invalid) {
            return false;
        }
    }

    /**
     * Only the active channel is sent: a browser holds one subscription, and rows of the other channel are
     * replaced when the app re-registers. FCM rows also wait while FCM credentials are missing.
     */
    public boolean sendsTransport(String transport) {
        boolean webPushRow = "WEB_PUSH".equals(transport);
        return PROVIDER_WEB_PUSH.equals(resolvedProvider()) ? webPushRow : !webPushRow && fcmReady();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public String getSenderMode() {
        return senderMode;
    }

    public void setSenderMode(String senderMode) {
        this.senderMode = senderMode;
    }

    public boolean isNativeImageStrict() {
        return nativeImageStrict;
    }

    public void setNativeImageStrict(boolean nativeImageStrict) {
        this.nativeImageStrict = nativeImageStrict;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public Duration getStaleRegistrationAge() {
        return staleRegistrationAge;
    }

    public void setStaleRegistrationAge(Duration staleRegistrationAge) {
        this.staleRegistrationAge = staleRegistrationAge;
    }

    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    public void setMaxPayloadBytes(int maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    public Worker getWorker() {
        return worker;
    }

    public static class Credentials {
        private String mode = "adc";
        private String serviceAccountPath = "";
        private String staticAccessToken = "";
        private String registrationTokenEncryptionKey = "";
        private boolean registrationTokenEncryptionRequired = false;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getServiceAccountPath() {
            return serviceAccountPath;
        }

        public void setServiceAccountPath(String serviceAccountPath) {
            this.serviceAccountPath = serviceAccountPath;
        }

        public String getStaticAccessToken() {
            return staticAccessToken;
        }

        public void setStaticAccessToken(String staticAccessToken) {
            this.staticAccessToken = staticAccessToken;
        }

        public String getRegistrationTokenEncryptionKey() {
            return registrationTokenEncryptionKey;
        }

        public void setRegistrationTokenEncryptionKey(String registrationTokenEncryptionKey) {
            this.registrationTokenEncryptionKey = registrationTokenEncryptionKey;
        }

        public boolean isRegistrationTokenEncryptionRequired() {
            return registrationTokenEncryptionRequired;
        }

        public void setRegistrationTokenEncryptionRequired(boolean registrationTokenEncryptionRequired) {
            this.registrationTokenEncryptionRequired = registrationTokenEncryptionRequired;
        }
    }

    public static class Worker {
        private boolean enabled = true;
        private int pollSize = 200;
        private int maxAttempts = 8;
        private Duration leaseTimeout = Duration.ofSeconds(60);
        private Duration initialBackoff = Duration.ofSeconds(10);
        private Duration maxBackoff = Duration.ofHours(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPollSize() {
            return pollSize;
        }

        public void setPollSize(int pollSize) {
            this.pollSize = pollSize;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getLeaseTimeout() {
            return leaseTimeout;
        }

        public void setLeaseTimeout(Duration leaseTimeout) {
            this.leaseTimeout = leaseTimeout;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }
    }
}

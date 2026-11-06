package com.erg.ergspringboot.notifications.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** VAPID key pair for standard Web Push. Web Push is disabled until both keys and the subject are set. */
@Component
@ConfigurationProperties(prefix = "erg.notifications.web-push")
public class WebPushProperties {

    /** Base64url uncompressed P-256 public key; also handed to browsers as applicationServerKey. */
    private String publicKey = "";
    /** Base64url P-256 private key. Secret: never log. */
    private String privateKey = "";
    /** VAPID subject, a mailto: or https: contact URL. */
    private String subject = "";

    public boolean isConfigured() {
        return !isBlank(publicKey) && !isBlank(privateKey) && !isBlank(subject);
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey == null ? "" : publicKey.trim();
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey == null ? "" : privateKey.trim();
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject == null ? "" : subject.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

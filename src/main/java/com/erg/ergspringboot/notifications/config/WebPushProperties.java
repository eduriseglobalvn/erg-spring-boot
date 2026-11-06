package com.erg.ergspringboot.notifications.config;

import java.util.ArrayList;
import java.util.List;
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
    /** Push service hosts the server may POST to (matched on a DNS label boundary). */
    private List<String> allowedHostSuffixes = new ArrayList<>(List.of(
            "fcm.googleapis.com", "push.services.mozilla.com", "notify.windows.com", "push.apple.com"));

    public List<String> getAllowedHostSuffixes() {
        return allowedHostSuffixes;
    }

    public void setAllowedHostSuffixes(List<String> allowedHostSuffixes) {
        this.allowedHostSuffixes = allowedHostSuffixes == null ? new ArrayList<>() : new ArrayList<>(allowedHostSuffixes);
    }

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

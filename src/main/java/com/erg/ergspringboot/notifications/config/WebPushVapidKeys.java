package com.erg.ergspringboot.notifications.config;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.util.Arrays;
import java.util.List;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validated VAPID key pair. Missing, malformed or mismatched keys (or a subject that is not mailto:/https:)
 * never abort boot: Web Push is then disabled and a sanitized warning (no key material) is logged.
 */
@Component
public class WebPushVapidKeys {

    private static final Logger log = LoggerFactory.getLogger(WebPushVapidKeys.class);

    static {
        ensureProvider();
    }

    /** web-push loads EC keys through the "BC" JCA provider. */
    public static synchronized void ensureProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final PushService pushService;
    private final String publicKey;
    private final List<String> allowedHostSuffixes;

    public WebPushVapidKeys(WebPushProperties properties) {
        this.pushService = load(properties);
        this.publicKey = pushService == null ? null : properties.getPublicKey();
        this.allowedHostSuffixes = List.copyOf(properties.getAllowedHostSuffixes());
    }

    public boolean enabled() {
        return pushService != null;
    }

    /** Public applicationServerKey, or null when Web Push is disabled. */
    public String publicKey() {
        return publicKey;
    }

    /** Null when Web Push is disabled. */
    public PushService pushService() {
        return pushService;
    }

    public List<String> allowedHostSuffixes() {
        return allowedHostSuffixes;
    }

    private static PushService load(WebPushProperties properties) {
        if (!properties.isConfigured()) {
            return null;
        }
        String subject = properties.getSubject();
        if (!subject.startsWith("mailto:") && !subject.startsWith("https://")) {
            log.warn("Web Push disabled: erg.notifications.web-push.subject must start with mailto: or https://");
            return null;
        }
        try {
            PublicKey publicKey = Utils.loadPublicKey(properties.getPublicKey());
            PrivateKey privateKey = Utils.loadPrivateKey(properties.getPrivateKey());
            byte[] derived = Utils.encode(Utils.loadPublicKey((ECPrivateKey) privateKey));
            if (!Arrays.equals(derived, Utils.encode((ECPublicKey) publicKey))) {
                log.warn("Web Push disabled: erg.notifications.web-push public-key does not match private-key");
                return null;
            }
            return new PushService(new KeyPair(publicKey, privateKey), subject);
        } catch (Exception | LinkageError exception) {
            // Only the exception type: messages of key parsers can echo key material.
            log.warn("Web Push disabled: invalid VAPID keys in erg.notifications.web-push ({})",
                    exception.getClass().getSimpleName());
            return null;
        }
    }
}

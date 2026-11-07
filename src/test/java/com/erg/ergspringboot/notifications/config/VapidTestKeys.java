package com.erg.ergspringboot.notifications.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;

/** Freshly generated VAPID key pairs in the base64url form erg.notifications.web-push expects. */
public final class VapidTestKeys {

    private VapidTestKeys() { }

    /** [publicKey, privateKey] */
    public static String[] generate() {
        try {
            WebPushVapidKeys.ensureProvider();
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", "BC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return new String[] {
                    encoder.encodeToString(Utils.encode((ECPublicKey) pair.getPublic())),
                    encoder.encodeToString(Utils.encode((ECPrivateKey) pair.getPrivate()))
            };
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static WebPushProperties properties() {
        String[] keys = generate();
        WebPushProperties properties = new WebPushProperties();
        properties.setPublicKey(keys[0]);
        properties.setPrivateKey(keys[1]);
        properties.setSubject("mailto:ops@example.test");
        return properties;
    }
}

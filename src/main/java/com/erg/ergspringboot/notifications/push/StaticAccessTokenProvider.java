package com.erg.ergspringboot.notifications.push;

import java.util.Optional;

public class StaticAccessTokenProvider implements AccessTokenProvider {

    private final String accessToken;

    public StaticAccessTokenProvider(String accessToken) {
        this.accessToken = accessToken;
    }

    @Override
    public Optional<String> accessToken() {
        return Optional.of(accessToken);
    }
}

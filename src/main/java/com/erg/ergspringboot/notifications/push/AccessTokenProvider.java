package com.erg.ergspringboot.notifications.push;

import java.util.Optional;

@FunctionalInterface
public interface AccessTokenProvider {

    Optional<String> accessToken();
}

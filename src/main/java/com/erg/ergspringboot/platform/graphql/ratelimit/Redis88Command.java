package com.erg.ergspringboot.platform.graphql.ratelimit;

import java.nio.charset.StandardCharsets;
import io.lettuce.core.protocol.ProtocolKeyword;

enum Redis88Command implements ProtocolKeyword {
    INCREX;

    private final byte[] bytes = name().getBytes(StandardCharsets.US_ASCII);

    @Override
    public byte[] getBytes() {
        return bytes;
    }
}


package com.erg.ergspringboot.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdGeneratorTest {

    @Test
    void newIdReturnsUuidV7() {
        UUID id = UUID.fromString(IdGenerator.newId());

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }
}

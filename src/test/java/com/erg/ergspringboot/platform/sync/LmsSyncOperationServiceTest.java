package com.erg.ergspringboot.platform.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class LmsSyncOperationServiceTest {

    @Test
    void payloadHashIsStableForEquivalentMapOrdering() {
        LmsSyncOperationService service = new LmsSyncOperationService(mock(JdbcTemplate.class), new ObjectMapper());
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("answer", "A");
        first.put("expectedVersion", 3);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("expectedVersion", 3);
        second.put("answer", "A");

        assertThat(service.payloadHash(first)).isEqualTo(service.payloadHash(second));
        assertThat(service.serverTime().getOffset().getTotalSeconds()).isZero();
    }

    @Test
    void nullPayloadHashesAsAnEmptyObject() {
        LmsSyncOperationService service = new LmsSyncOperationService(mock(JdbcTemplate.class), new ObjectMapper());

        assertThat(service.payloadHash(null)).isEqualTo(service.payloadHash(Map.of()));
    }
}

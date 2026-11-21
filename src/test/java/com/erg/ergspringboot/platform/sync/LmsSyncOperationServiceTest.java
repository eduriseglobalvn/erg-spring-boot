package com.erg.ergspringboot.platform.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.LinkedHashMap;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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

    @Test
    @SuppressWarnings("unchecked")
    void duplicateKeyReplaysTheStoredResultWithoutRelyingOnAnAbortedTransaction() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LmsSyncOperationService service = new LmsSyncOperationService(jdbc, new ObjectMapper());
        Map<String, Object> payload = Map.of("answer", "A");
        OffsetDateTime now = OffsetDateTime.now();
        var stored = new LmsSyncOperationService.Receipt("erg", "usr-1", "k1", "k1", "quiz.attempt.submit", "att-1",
                service.payloadHash(payload), "SUCCEEDED", 3L, "{}", now, now, false);
        doReturn(0).when(jdbc).update(anyString(), any(Object[].class));
        doReturn(List.of(stored)).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        var receipt = service.reserve("erg", "usr-1", "k1", "k1", "quiz.attempt.submit", "att-1", payload, 3L);

        assertThat(receipt).isPresent();
        assertThat(receipt.get().replayed()).isTrue();
    }
}

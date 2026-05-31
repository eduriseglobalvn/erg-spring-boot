package com.erg.ergspringboot.model.lms;

import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/lms/application/service/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttemptAnswer {

    private String questionId;

    private Object answer;

    private Map<String, Object> clientResult;

    private OffsetDateTime answeredAt;
}

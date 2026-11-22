package com.erg.ergspringboot.lms.internal.attempt;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptPolicySnapshotResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptSubmitResponse;
import com.erg.ergspringboot.lms.persistence.Attempt;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maps a stored attempt to its wire responses. */
final class AttemptResponses {
    private AttemptResponses() {
    }

    static AttemptSubmitResponse submit(Attempt attempt) {
        return new AttemptSubmitResponse(value(attempt.getScore()), value(attempt.getMaxScore()),
                value(attempt.getPercent()), Boolean.TRUE.equals(attempt.getPassed()));
    }

    static AttemptResponse of(Attempt a) {
        Map<String, com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptAnswer> out = new LinkedHashMap<>();
        map(a.getAnswers()).forEach((k, v) -> out.put(k, new com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptAnswer(
                v == null ? null : v.getAnswer(), v == null ? Map.of() : map(v.getClientResult()),
                v == null ? null : v.getAnsweredAt())));
        return new AttemptResponse(a.getId(), a.getAssignmentId(), a.getQuizId(), a.getStudentId(), a.getPackageId(),
                a.getPackageHash(), a.getStatus(), out, value(a.getScore()), value(a.getMaxScore()), value(a.getPercent()),
                Boolean.TRUE.equals(a.getPassed()), a.getStartedAt(), a.getSubmittedAt(), a.getUpdatedAt(),
                AttemptPolicySnapshotResponse.from(a.getPolicySnapshot()));
    }

    private static double value(Double value) {
        return value == null ? 0 : value;
    }

    private static <K, V> Map<K, V> map(Map<K, V> value) {
        return value == null ? Map.of() : value;
    }
}

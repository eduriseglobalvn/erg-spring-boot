package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptSubmitResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StartAttemptRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.SubmitAttemptRequest;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.lms.LmsRuntimeService;
import org.springframework.stereotype.Service;

/** Public LMS command facade for the attempt hot path. */
@Service
public class LmsOperations {
    private final LmsRuntimeService legacy;

    public LmsOperations(LmsRuntimeService legacy) {
        this.legacy = legacy;
    }

    public AttemptResponse startAttempt(String studentId, StartAttemptRequest request,
            String idempotencyKey, UserAccessContext access) {
        return legacy.startAttempt(studentId, request, idempotencyKey, access);
    }

    public AttemptSubmitResponse submitAttempt(String attemptId, SubmitAttemptRequest request,
            UserAccessContext access) {
        return legacy.submitAttempt(attemptId, request, access);
    }
}

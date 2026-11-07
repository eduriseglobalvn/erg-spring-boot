package com.erg.ergspringboot.timekeeping;

import com.erg.ergspringboot.exception.ApiException;
import org.springframework.http.HttpStatus;

/** Business rejection with a stable code (e.g. FACE_CONSENT_REQUIRED, PERIOD_LOCKED) and a Vietnamese message. */
public class TimekeepingException extends ApiException {

    public TimekeepingException(HttpStatus status, String code, String messageVi) {
        super(status, code, status.getReasonPhrase(), messageVi);
    }
}

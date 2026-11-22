package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.exception.ApiException;
import org.springframework.http.HttpStatus;

/** An assignment that students already started cannot be deleted; stable code {@code ASSIGNMENT_HAS_ATTEMPTS}. */
public class AssignmentHasAttemptsException extends ApiException {

    public AssignmentHasAttemptsException() {
        super(HttpStatus.CONFLICT, "ASSIGNMENT_HAS_ATTEMPTS", "Conflict",
                "Bài tập đã có học sinh làm bài nên không thể xóa. Hãy đóng bài tập thay vì xóa.");
    }
}

package com.erg.ergspringboot.service.curriculum;

import com.erg.ergspringboot.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CurriculumException extends ApiException {

    private CurriculumException(HttpStatus status, String code, String message) {
        super(status, code, status.getReasonPhrase(), message);
    }

    public static CurriculumException badRequest(String code, String message) {
        return new CurriculumException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static CurriculumException notFound(String nodeType, String id) {
        return new CurriculumException(
                HttpStatus.NOT_FOUND,
                "CURRICULUM_NODE_NOT_FOUND",
                "curriculum " + nodeType + " not found: " + id
        );
    }

    public static CurriculumException conflict(String code, String message) {
        return new CurriculumException(HttpStatus.CONFLICT, code, message);
    }
}

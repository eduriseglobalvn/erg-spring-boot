package com.erg.ergspringboot.exception;

import org.springframework.http.HttpStatus;

public class NotImplementedException extends ApiException {

    public NotImplementedException(String code, String message) {
        super(HttpStatus.NOT_IMPLEMENTED, code, "Not Implemented", message);
    }
}

package com.erg.ergspringboot.exception;

import org.springframework.http.HttpStatus;

public class AccountLifecycleException extends ApiException {

    public AccountLifecycleException(HttpStatus status, String code, String message) {
        super(status, code, "Account lifecycle error", message);
    }
}

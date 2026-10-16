package com.erg.ergspringboot.content.lifecycle;

import com.erg.ergspringboot.exception.ApiException;
import org.springframework.http.HttpStatus;

public class LifecycleTransitionException extends ApiException {
    public LifecycleTransitionException(String code, String message) {
        super(HttpStatus.CONFLICT, code, "Content lifecycle conflict", message);
    }
}

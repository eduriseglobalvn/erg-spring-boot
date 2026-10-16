package com.erg.ergspringboot.common.response;

import java.util.List;

public record ErrDetail(
        String code,
        String message,
        List<FieldErrorDetail> details
) {

    public ErrDetail {
        details = List.copyOf(details);
    }

    public static ErrDetail of(String code, String message) {
        return new ErrDetail(code, message, List.of());
    }
}

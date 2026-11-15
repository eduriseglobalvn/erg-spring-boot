package com.erg.ergspringboot.exception;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.platform.web.RequestCorrelation;
import java.time.OffsetDateTime;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/**
 * 409 HOLIDAY_AFFECTS_PAST_PERIODS with the impact in {@code data}: the one error whose body carries a payload the
 * client must show (dates, taughtPeriods, reportedPeriods, schools). Runs before the general handler.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HolidayExceptionHandler {

    @ExceptionHandler(HolidayAffectsPastPeriodsException.class)
    public ResponseEntity<ApiResponse<Object>> handle(HolidayAffectsPastPeriodsException exception, WebRequest request) {
        String path = null;
        String requestId = request.getHeader(RequestCorrelation.REQUEST_ID_HEADER);
        if (request instanceof ServletWebRequest servlet) {
            path = servlet.getRequest().getRequestURI();
            requestId = RequestCorrelation.current(servlet.getRequest());
        }
        ApiResponse<Object> body = new ApiResponse<>(exception.getStatus().value(), exception.getMessage(),
                exception.payload(), ApiResponse.TransportError.code(exception.getCode()), OffsetDateTime.now(), path, requestId);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.getStatus());
        if (requestId != null) response.header(RequestCorrelation.REQUEST_ID_HEADER, requestId);
        return response.body(body);
    }
}

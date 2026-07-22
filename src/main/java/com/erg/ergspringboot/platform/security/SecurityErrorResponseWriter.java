package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.ErrDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String requestId = requestId(request);
        response.setHeader("X-Request-ID", requestId);
        response.setHeader("Cache-Control", "no-store");
        ApiResponse<Void> body = ApiResponse.failure(
                status,
                new ErrDetail(code, message, java.util.List.of()),
                request.getRequestURI(),
                requestId
        );
        objectMapper.writeValue(response.getWriter(), body);
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}

package com.erg.ergspringboot.audit.capture;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * X-003: a create has no path variable, so the id of the new object is read from the response body and left
 * on the request for {@link MutationAuditInterceptor}, which runs after the body is written.
 */
@ControllerAdvice
public class CreatedResourceIdAdvice implements ResponseBodyAdvice<Object> {
    static final String ATTRIBUTE = CreatedResourceIdAdvice.class.getName() + ".id";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (request instanceof ServletServerHttpRequest servlet && "POST".equals(servlet.getServletRequest().getMethod())) {
            String id = AuditOutboxWriter.idOf(body);
            if (id != null) servlet.getServletRequest().setAttribute(ATTRIBUTE, id);
        }
        return body;
    }
}

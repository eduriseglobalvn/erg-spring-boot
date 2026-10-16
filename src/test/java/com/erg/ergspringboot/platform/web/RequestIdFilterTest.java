package com.erg.ergspringboot.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatedRequestIdIsVisibleToDownstreamRestAndGraphQlProcessing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/graphql");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<HttpServletRequest> downstream = new AtomicReference<>();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) ->
                downstream.set((HttpServletRequest) filteredRequest));

        String generated = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(generated).isNotBlank();
        assertThat(downstream.get().getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo(generated);
        assertThat(downstream.get().getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo(generated);
    }

    @Test
    void suppliedRequestIdIsTrimmedAndReusedEverywhere() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "  req-client  ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<HttpServletRequest> downstream = new AtomicReference<>();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) ->
                downstream.set((HttpServletRequest) filteredRequest));

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("req-client");
        assertThat(downstream.get().getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo("req-client");
        assertThat(downstream.get().getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("req-client");
    }
}

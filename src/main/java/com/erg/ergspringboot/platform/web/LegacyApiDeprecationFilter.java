package com.erg.ergspringboot.platform.web;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public final class LegacyApiDeprecationFilter extends OncePerRequestFilter {

    private final LegacyApiRegistry routes;
    private final MeterRegistry meters;

    public LegacyApiDeprecationFilter(LegacyApiRegistry routes, MeterRegistry meters) {
        this.routes = routes;
        this.meters = meters;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        routes.find(request.getRequestURI()).ifPresent(route -> apply(route, request, response));
        filterChain.doFilter(request, response);
    }

    private void apply(
            LegacyApiRegistry.LegacyRoute route,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        response.setHeader("Deprecation", "true");
        response.setHeader("Sunset", route.sunset().atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME));
        response.setHeader("X-ERG-API-Owner", route.owner());
        String successor = route.successor(request.getRequestURI());
        if (successor != null) {
            response.setHeader("Link", "<" + successor + ">; rel=\"successor-version\"");
        }
        Counter.builder("erg.api.deprecated.requests")
                .description("Requests served by a registered legacy API route")
                .tag("route_group", route.routeGroup())
                .tag("owner", route.owner())
                .register(meters)
                .increment();
    }
}

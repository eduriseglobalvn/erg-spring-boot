package com.erg.ergspringboot.platform.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

@Component
public final class LegacyApiRegistry {

    private final AntPathMatcher paths = new AntPathMatcher();
    private final List<LegacyRoute> routes;

    public LegacyApiRegistry() {
        this(LocalDate.now(java.time.Clock.systemUTC()));
    }

    LegacyApiRegistry(LocalDate today) {
        routes = List.of(
                new LegacyRoute("/api/crawler/**", "crawler-legacy", "platform-crawler",
                        LocalDate.of(2026, 10, 31), "/api/crawler", "/api/v1/crawler"),
                new LegacyRoute("/api/content/**", "learning-content-legacy", "learningcontent",
                        LocalDate.of(2026, 12, 31), null, null),
                new LegacyRoute("/api/curriculum/topics/**", "curriculum-content-legacy", "learningcontent",
                        LocalDate.of(2026, 12, 31), null, null)
        );
    }

    public Optional<LegacyRoute> find(String requestPath) {
        return routes.stream().filter(route -> paths.match(route.pattern(), requestPath)).findFirst();
    }

    public record LegacyRoute(
            String pattern,
            String routeGroup,
            String owner,
            LocalDate sunset,
            String legacyPrefix,
            String canonicalPrefix
    ) {
        public String successor(String requestPath) {
            if (legacyPrefix == null || canonicalPrefix == null || !requestPath.startsWith(legacyPrefix)) {
                return null;
            }
            return canonicalPrefix + requestPath.substring(legacyPrefix.length());
        }
    }
}

package com.erg.ergspringboot.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ErgAuthenticationFilter authenticationFilter;
    private final PermissionAuthorizationManager permissions;
    private final SecurityErrorResponseWriter errorWriter;
    private final PrometheusAccessRequestMatcher prometheusAccessRequestMatcher;
    private final ErgAuthProperties authProperties;

    @Autowired(required = false)
    private OidcPrincipalBridgeFilter oidcPrincipalBridgeFilter;

    public SecurityConfig(
            ErgAuthenticationFilter authenticationFilter,
            PermissionAuthorizationManager permissions,
            SecurityErrorResponseWriter errorWriter,
            PrometheusAccessRequestMatcher prometheusAccessRequestMatcher,
            ErgAuthProperties authProperties
    ) {
        this.authenticationFilter = authenticationFilter;
        this.permissions = permissions;
        this.errorWriter = errorWriter;
        this.prometheusAccessRequestMatcher = prometheusAccessRequestMatcher;
        this.authProperties = authProperties;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (authProperties.isBffEnabled()) {
            CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
            csrfRepository.setCookiePath("/");
            csrfRepository.setCookieCustomizer(cookie -> cookie
                    .path("/")
                    .secure(true)
                    .sameSite("Lax"));
            http.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                    .logout(logout -> logout
                            .logoutUrl("/api/v1/auth/logout")
                            .invalidateHttpSession(true)
                            .clearAuthentication(true)
                            .deleteCookies("__Host-erg_session")
                            .logoutSuccessHandler((request, response, authentication) ->
                                    response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                    .oauth2Login(oauth -> oauth
                            .loginPage("/oauth2/authorization/erg-api")
                            .successHandler((request, response, authentication) -> {
                                Object stored = request.getSession().getAttribute("erg.auth.returnTo");
                                String returnTo = stored instanceof String value
                                        && value.startsWith("/") && !value.startsWith("//") ? value : "/";
                                request.getSession().removeAttribute("erg.auth.returnTo");
                                response.sendRedirect(returnTo);
                            }));
        } else {
            http.csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        }

        http.exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) -> {
                            Object failureCode = request.getAttribute(ErgAuthenticationFilter.AUTH_FAILURE_CODE_ATTRIBUTE);
                            boolean sessionReplaced = "AUTH_SESSION_REPLACED".equals(failureCode);
                            errorWriter.write(
                                    request,
                                    response,
                                    401,
                                    sessionReplaced ? "AUTH_SESSION_REPLACED" : "AUTHENTICATION_REQUIRED",
                                    sessionReplaced
                                            ? "This session was replaced by a login on another device"
                                            : "Authentication is required"
                            );
                        })
                        .accessDeniedHandler((request, response, ex) ->
                                errorWriter.write(request, response, 403, "ACCESS_DENIED", "You do not have permission to access this resource"))
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(prometheusAccessRequestMatcher).permitAll()
                        .requestMatchers(
                                "/api/v1/health", "/api/v1/ready", "/api/health", "/api/ready",
                                "/healthz", "/ready", "/metrics",
                                "/v3/api-docs", "/v3/api-docs/**", "/scalar", "/scalar/**",
                                "/robots.txt", "/sitemap.xml", "/sitemap_index.xml", "/sitemap-*.xml"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/webhooks/discord", "/webhooks/telegram").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login", "/api/v1/auth/refresh"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/login", "/oauth2/**", "/login/oauth2/**").permitAll()
                        // CMS has a deliberately public read surface, but editorial-only
                        // variants must be matched before the broad public posts rule below.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/posts/hidden", "/api/v1/posts/trash", "/api/v1/posts/preview/**")
                        .access((auth, context) -> permissions.requireAny(auth, context,
                                List.of("cms.posts.update", "cms.posts.delete", "*")))
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/reviews/admin", "/api/v1/reviews/admin/all")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/pages/**", "/api/v1/menus/structure",
                                "/api/v1/posts", "/api/v1/posts/**",
                                "/api/v1/courses", "/api/v1/courses/**",
                                "/api/v1/elearning/categories/**", "/api/v1/elearning/levels/**",
                                "/api/v1/recruitment/jobs/**", "/api/v1/reviews", "/api/v1/reviews/stats",
                                "/api/v1/public-disclosure/**", "/api/v1/trending/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/recruitment/apply", "/api/v1/reviews", "/api/v1/reviews/*/helpful"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/graphql").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/roles/**", "/api/v1/permissions/**", "/api/v1/permission-groups/**")
                        .access((auth, context) -> permissions.require("rbac.role.read", auth, context))
                        .requestMatchers(HttpMethod.POST, "/api/v1/roles")
                        .access((auth, context) -> permissions.require("rbac.role.create", auth, context))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/roles/**")
                        .access((auth, context) -> permissions.require("rbac.role.update", auth, context))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/roles/**")
                        .access((auth, context) -> permissions.require("rbac.role.update", auth, context))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/roles/**")
                        .access((auth, context) -> permissions.require("rbac.role.delete", auth, context))
                        .requestMatchers("/api/v1/users/*/roles/**", "/api/v1/users/*/role-assignments/**", "/api/v1/users/*/permissions/**", "/api/v1/users/roles/bulk")
                        .access((auth, context) -> permissions.require("rbac.role.assign", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*/effective-permissions", "/api/v1/users/*/permission-overrides")
                        .access((auth, context) -> permissions.require("rbac.permission.read", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("account.self.read", "profile.self", "*")))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("account.self.update", "profile.self", "*")))
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/me/recovery-email/**")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("account.self.recovery_email.update", "profile.self", "*")))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/me/password")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("account.self.password.change", "profile.self", "*")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/sessions", "/api/v1/sessions/**", "/api/v1/session/**")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("account.self.sessions.read", "account.self.read", "profile.self", "*")))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/me/sessions/**")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("account.self.sessions.read", "profile.self", "*")))
                        .requestMatchers("/api/v1/users/me/**")
                        .access((auth, context) -> permissions.require("profile.self", auth, context))
                        .requestMatchers("/api/v1/users/**")
                        .access((auth, context) -> permissions.require("users.manage", auth, context))
                        .requestMatchers("/api/v1/admin/users/**")
                        .access((auth, context) -> permissions.require("users.manage", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/crm/seo/*/performance", "/api/v1/crm/analytics/**")
                        .access((auth, context) -> permissions.require("crm.analytics.read", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/crm/**")
                        .access((auth, context) -> permissions.require("crm.school.read", auth, context))
                        .requestMatchers("/api/v1/crm/**")
                        .access((auth, context) -> permissions.require("crm.deal.manage", auth, context))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/lcms/media/upload/quiz-media/**").permitAll()
                        .requestMatchers("/api/v1/ai-content/keys/**")
                        .access((auth, context) -> permissions.require("api-keys.manage", auth, context))
                        .requestMatchers("/api/v1/ai-content/**")
                        .access((auth, context) -> permissions.require("ai-content.use", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/curriculum/**", "/api/curriculum/**", "/api/v1/content/**", "/api/content/**")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("content.resource.read", "content.manage", "lms.resource.read", "*")))
                        .requestMatchers(HttpMethod.POST, "/api/v1/content/**", "/api/content/items", "/api/content/assets", "/api/content/lectures/google-slides")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("lms.resource.create", "content.manage", "*")))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/content/**", "/api/content/items/**", "/api/content/assets/**")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("lms.resource.update", "content.manage", "*")))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/content/**", "/api/content/items/**", "/api/content/assets/**")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("lms.resource.delete", "content.manage", "*")))
                        .requestMatchers("/api/v1/curriculum/topics/*/content-items/**", "/api/curriculum/topics/*/content-items/**")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("lms.resource.share", "content.manage", "curriculum.manage", "*")))
                        .requestMatchers("/api/v1/curriculum/**", "/api/curriculum/**", "/api/v1/content/**", "/api/content/**")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("content.manage", "curriculum.manage", "*")))
                        .requestMatchers("/api/v1/operations/**", "/api/v1/audit/**", "/api/v1/operations/firewall/**")
                        .access((auth, context) -> permissions.require("operations.manage", auth, context))
                        .requestMatchers("/api/v1/migration/**", "/api/v1/platform/**")
                        .access((auth, context) -> permissions.require("platform.read", auth, context))
                        .requestMatchers("/api/v1/crawler/**", "/api/crawler/**", "/api/v1/rss/**")
                        .access((auth, context) -> permissions.require("crawler.manage", auth, context))
                        // Self notification API — requires profile.self only (not notifications.manage)
                        .requestMatchers(HttpMethod.GET, "/api/v1/notifications", "/api/v1/notifications/unread-count")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("lms.notification.read", "profile.self", "*")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/notifications/{id}")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("lms.notification.read", "profile.self", "*")))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/notifications/{id}/read", "/api/v1/notifications/read-all")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("lms.notification.read", "profile.self", "*")))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/notifications/{id}")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("lms.notification.read", "profile.self", "*")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/notifications/preferences")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("lms.notification.read", "profile.self", "*")))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/notifications/preferences")
                        .access((auth, context) -> permissions.requireAny(auth, context, List.of("lms.notification.preferences.update", "profile.self", "*")))
                        // Self device API
                        .requestMatchers(HttpMethod.POST, "/api/v1/notification-devices",
                                "/api/v1/notification-devices/unregister", "/api/v1/notification-devices/heartbeat")
                        .access((auth, context) -> permissions.require("profile.self", auth, context))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/notification-devices/{deviceId}")
                        .access((auth, context) -> permissions.require("profile.self", auth, context))
                        // Admin notification API — requires notifications.manage
                        .requestMatchers("/api/v1/admin/notifications/**")
                        .access((auth, context) -> permissions.require("notifications.manage", auth, context))
                        // Fallback catch-all for other notification routes
                        .requestMatchers("/api/v1/notifications/**")
                        .access((auth, context) -> permissions.require("notifications.manage", auth, context))
                        .requestMatchers("/api/v1/admin/elearning/**", "/api/v1/elearning/**")
                        .access((auth, context) -> permissions.require("elearning.manage", auth, context))
                        .requestMatchers("/api/v1/seo/**")
                        .access((auth, context) -> permissions.require("seo.manage", auth, context))
                        .requestMatchers(HttpMethod.POST, "/api/v1/posts", "/api/v1/posts/preview")
                        .access((auth, context) -> permissions.require("cms.posts.create", auth, context))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/posts/**")
                        .access((auth, context) -> permissions.require("cms.posts.update", auth, context))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/posts/**")
                        .access((auth, context) -> permissions.require("cms.posts.delete", auth, context))
                        .requestMatchers(HttpMethod.POST, "/api/v1/posts/categories")
                        .access((auth, context) -> permissions.require("cms.categories.create", auth, context))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/posts/categories/**")
                        .access((auth, context) -> permissions.require("cms.categories.update", auth, context))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/posts/categories/**")
                        .access((auth, context) -> permissions.require("cms.categories.delete", auth, context))
                        .requestMatchers(HttpMethod.POST, "/api/v1/posts/images/**")
                        .access((auth, context) -> permissions.require("media.upload", auth, context))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/posts/images/**")
                        .access((auth, context) -> permissions.require("media.delete", auth, context))
                        .requestMatchers(HttpMethod.POST, "/api/v1/courses")
                        .access((auth, context) -> permissions.require("courses.create", auth, context))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/courses/**")
                        .access((auth, context) -> permissions.require("courses.update", auth, context))
                        .requestMatchers(HttpMethod.POST, "/api/v1/courses/**")
                        .access((auth, context) -> permissions.require("courses.update", auth, context))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/courses/**")
                        .access((auth, context) -> permissions.require("courses.delete", auth, context))
                        .requestMatchers("/api/v1/documents/**")
                        .access((auth, context) -> permissions.require("documents.manage", auth, context))
                        .requestMatchers(HttpMethod.POST, "/api/v1/public-disclosure/**")
                        .access((auth, context) -> permissions.require("public-disclosure.manage", auth, context))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/public-disclosure/**")
                        .access((auth, context) -> permissions.require("public-disclosure.manage", auth, context))
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll()
                )
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class);
        if (oidcPrincipalBridgeFilter != null) {
            http.addFilterAfter(oidcPrincipalBridgeFilter, OAuth2LoginAuthenticationFilter.class);
        }
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    @SuppressWarnings("unused")
    private String requestPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}

package com.erg.ergspringboot.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ErgAuthenticationFilter authenticationFilter;
    private final PermissionAuthorizationManager permissions;
    private final SecurityErrorResponseWriter errorWriter;

    public SecurityConfig(
            ErgAuthenticationFilter authenticationFilter,
            PermissionAuthorizationManager permissions,
            SecurityErrorResponseWriter errorWriter
    ) {
        this.authenticationFilter = authenticationFilter;
        this.permissions = permissions;
        this.errorWriter = errorWriter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) ->
                                errorWriter.write(request, response, 401, "AUTHENTICATION_REQUIRED", "Authentication is required"))
                        .accessDeniedHandler((request, response, ex) ->
                                errorWriter.write(request, response, 403, "ACCESS_DENIED", "You do not have permission to access this resource"))
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/v1/health", "/api/v1/ready", "/api/health", "/api/ready",
                                "/healthz", "/ready", "/metrics",
                                "/v3/api-docs", "/v3/api-docs/**", "/scalar", "/scalar/**",
                                "/robots.txt", "/sitemap.xml", "/sitemap_index.xml", "/sitemap-*.xml",
                                "/webhooks/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/verify-pin", "/api/v1/auth/resend-pin",
                                "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
                                "/api/v1/auth/google/login", "/api/v1/auth/oauth/apple"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/oauth/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/pages/**", "/api/v1/menus/structure",
                                "/api/v1/posts", "/api/v1/posts/**",
                                "/api/v1/courses", "/api/v1/courses/**",
                                "/api/v1/elearning/categories/**", "/api/v1/elearning/levels/**",
                                "/api/v1/hoclieu/home", "/api/v1/hoclieu/community/**",
                                "/api/v1/recruitment/jobs/**", "/api/v1/reviews", "/api/v1/reviews/stats",
                                "/api/v1/public-disclosure/**", "/api/v1/trending/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/recruitment/apply", "/api/v1/reviews", "/api/v1/reviews/*/helpful"
                        ).permitAll()
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
                        .requestMatchers("/api/v1/users/*/roles/**", "/api/v1/users/*/permissions/**", "/api/v1/users/roles/bulk")
                        .access((auth, context) -> permissions.require("rbac.role.assign", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*/effective-permissions", "/api/v1/users/*/permission-overrides")
                        .access((auth, context) -> permissions.require("rbac.permission.read", auth, context))
                        .requestMatchers("/api/v1/users/me/**", "/api/v1/sessions/**", "/api/v1/session/**")
                        .access((auth, context) -> permissions.require("profile.self", auth, context))
                        .requestMatchers("/api/v1/users/**")
                        .access((auth, context) -> permissions.require("users.manage", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/crm/seo/*/performance", "/api/v1/crm/analytics/**")
                        .access((auth, context) -> permissions.require("crm.analytics.read", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/crm/**")
                        .access((auth, context) -> permissions.require("crm.school.read", auth, context))
                        .requestMatchers("/api/v1/crm/**")
                        .access((auth, context) -> permissions.require("crm.deal.manage", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/lms/students/me/**")
                        .access((auth, context) -> permissions.require("lms.assignment.read_self", auth, context))
                        .requestMatchers("/api/v1/lms/attempts/**")
                        .access((auth, context) -> permissions.require("lms.assignment.submit", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/lms/quizzes/*/package", "/api/v1/lms/quizzes/*")
                        .access((auth, context) -> permissions.require("lms.quiz.read", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/lms/quizzes/*/students", "/api/v1/lms/assignments/*/progress")
                        .access((auth, context) -> permissions.require("lms.grade.read", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/lms/access-management/**")
                        .access((auth, context) -> permissions.require("lms.scope.read", auth, context))
                        .requestMatchers(HttpMethod.POST, "/api/v1/lms/access-management/preview")
                        .access((auth, context) -> permissions.require("lms.scope.read", auth, context))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/lms/access-management/**")
                        .access((auth, context) -> permissions.require("lms.scope.update", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/lms/dashboard/**")
                        .access((auth, context) -> permissions.require("lms.dashboard.read", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/lms/centers", "/api/v1/lms/education-units")
                        .access((auth, context) -> permissions.require("lms.unit.read", auth, context))
                        .requestMatchers(HttpMethod.POST, "/api/v1/lms/centers")
                        .access((auth, context) -> permissions.require("lms.unit.create", auth, context))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/lms/centers/**")
                        .access((auth, context) -> permissions.require("lms.unit.update", auth, context))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/lms/centers/**")
                        .access((auth, context) -> permissions.require("lms.unit.update", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/lms/classes")
                        .access((auth, context) -> permissions.require("lms.class.read", auth, context))
                        .requestMatchers(HttpMethod.POST, "/api/v1/lms/classes")
                        .access((auth, context) -> permissions.require("lms.class.create", auth, context))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/lms/classes/**")
                        .access((auth, context) -> permissions.require("lms.class.update", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/lms/students")
                        .access((auth, context) -> permissions.require("lms.student.read", auth, context))
                        .requestMatchers(HttpMethod.POST, "/api/v1/lms/students", "/api/v1/lms/students/bulk-accounts")
                        .access((auth, context) -> permissions.require("lms.student.create", auth, context))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/lms/students/**")
                        .access((auth, context) -> permissions.require("lms.student.update", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/lms/subjects", "/api/v1/lms/levels", "/api/v1/lms/topics")
                        .access((auth, context) -> permissions.require("lms.content.read", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/lms/questions", "/api/v1/lms/quizzes")
                        .access((auth, context) -> permissions.require("lms.quiz.read", auth, context))
                        .requestMatchers(HttpMethod.POST, "/api/v1/lms/questions", "/api/v1/lms/questions/**", "/api/v1/lms/quizzes", "/api/v1/lms/quizzes/from-questions", "/api/v1/lms/quizzes/random")
                        .access((auth, context) -> permissions.require("lms.quiz.create", auth, context))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/lms/quizzes/*/publish")
                        .access((auth, context) -> permissions.require("lms.quiz.publish", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/lms/assignments")
                        .access((auth, context) -> permissions.require("lms.assignment.read", auth, context))
                        .requestMatchers(HttpMethod.POST, "/api/v1/lms/assignments", "/api/v1/lms/assignments/**")
                        .access((auth, context) -> permissions.require("lms.assignment.create", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/lms/reports/**")
                        .access((auth, context) -> permissions.require("lms.report.read", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/lms/discussions")
                        .access((auth, context) -> permissions.require("lms.discussion.read", auth, context))
                        .requestMatchers(HttpMethod.POST, "/api/v1/lms/discussions", "/api/v1/lms/discussions/**")
                        .access((auth, context) -> permissions.require("lms.discussion.write", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/lms/announcements")
                        .access((auth, context) -> permissions.require("lms.announcement.read", auth, context))
                        .requestMatchers(HttpMethod.POST, "/api/v1/lms/announcements")
                        .access((auth, context) -> permissions.require("lms.announcement.write", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/lms/internal-documents")
                        .access((auth, context) -> permissions.require("lms.document.read", auth, context))
                        .requestMatchers(HttpMethod.POST, "/api/v1/lms/internal-documents")
                        .access((auth, context) -> permissions.require("lms.document.write", auth, context))
                        .requestMatchers("/api/v1/lms/imports/**")
                        .access((auth, context) -> permissions.require("lms.import.manage", auth, context))
                        .requestMatchers("/api/v1/lms/scopes/**")
                        .access((auth, context) -> permissions.require("lms.scope.read", auth, context))
                        .requestMatchers("/api/v1/lms/**")
                        .access((auth, context) -> permissions.require("lms.admin", auth, context))
                        .requestMatchers("/api/v1/ai-content/keys/**")
                        .access((auth, context) -> permissions.require("api-keys.manage", auth, context))
                        .requestMatchers("/api/v1/ai-content/**")
                        .access((auth, context) -> permissions.require("ai-content.use", auth, context))
                        .requestMatchers("/api/v1/operations/**", "/api/v1/audit/**", "/api/v1/operations/firewall/**")
                        .access((auth, context) -> permissions.require("operations.manage", auth, context))
                        .requestMatchers("/api/v1/migration/**", "/api/v1/platform/**")
                        .access((auth, context) -> permissions.require("platform.read", auth, context))
                        .requestMatchers("/api/v1/crawler/**", "/api/crawler/**", "/api/v1/rss/**")
                        .access((auth, context) -> permissions.require("crawler.manage", auth, context))
                        .requestMatchers("/api/v1/notifications/**")
                        .access((auth, context) -> permissions.require("notifications.manage", auth, context))
                        .requestMatchers(HttpMethod.POST, "/api/v1/hoclieu/community/**")
                        .access((auth, context) -> permissions.require("community.write", auth, context))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/hoclieu/community/**")
                        .access((auth, context) -> permissions.require("community.write", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/hoclieu/teacher/**")
                        .access((auth, context) -> permissions.require("hoclieu.resource.read", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/hoclieu/library/bootstrap", "/api/v1/hoclieu/library/progress")
                        .access((auth, context) -> permissions.require("hoclieu.resource.read", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/hoclieu/studio/bootstrap")
                        .access((auth, context) -> permissions.require("hoclieu.manage", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/hoclieu/resources/**", "/api/v1/hoclieu/quizzes")
                        .access((auth, context) -> permissions.require("hoclieu.resource.read", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/hoclieu/assets/*/slides/**")
                        .access((auth, context) -> permissions.require("hoclieu.asset.launch", auth, context))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/hoclieu/assets/*/slides/*/annotations")
                        .access((auth, context) -> permissions.require("hoclieu.progress.write", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/hoclieu/assets/*/launch", "/api/v1/hoclieu/assets/*/stream", "/api/v1/hoclieu/assets/*/pages")
                        .access((auth, context) -> permissions.require("hoclieu.asset.launch", auth, context))
                        .requestMatchers(HttpMethod.GET, "/api/v1/hoclieu/assets/*/download")
                        .access((auth, context) -> permissions.require("hoclieu.asset.download", auth, context))
                        .requestMatchers("/api/v1/hoclieu/**", "/api/v1/admin/hoclieu/**")
                        .access((auth, context) -> permissions.require("hoclieu.manage", auth, context))
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
                        .anyRequest().permitAll()
                )
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class);
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

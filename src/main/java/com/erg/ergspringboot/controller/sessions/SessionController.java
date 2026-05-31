package com.erg.ergspringboot.controller.sessions;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapResponse;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionContextResponse;
import com.erg.ergspringboot.exception.UnauthorizedException;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.sessions.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping({"/api/v1/sessions/current", "/api/v1/session/current"})
    public ApiResponse<SessionBootstrapResponse> currentBootstrap(HttpServletRequest request) {
        return ApiResponse.success(
                200,
                "Session bootstrap loaded",
                sessionService.currentBootstrap(sessionId(request), tenantId(request), UserAccessContext.from(request)),
                request.getRequestURI(),
                requestId(request)
        );
    }

    @GetMapping("/api/v1/sessions/context")
    public ApiResponse<SessionContextResponse> currentSession(HttpServletRequest request) {
        return ApiResponse.success(
                200,
                "Session loaded",
                sessionService.currentSession(sessionId(request), tenantId(request)),
                request.getRequestURI(),
                requestId(request)
        );
    }

    private String sessionId(HttpServletRequest request) {
        String sessionId = request.getHeader("X-Session-ID");
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId.trim();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ErgPrincipal principal) {
            String principalSessionId = principal.sessionId();
            if (principalSessionId != null && !principalSessionId.isBlank() && !"token-session".equals(principalSessionId)) {
                return principalSessionId.trim();
            }
        }
        throw new UnauthorizedException("missing session id");
    }

    private String tenantId(HttpServletRequest request) {
        String tenantId = request.getHeader("X-Tenant-ID");
        return tenantId == null || tenantId.isBlank() ? "erg" : tenantId.trim();
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}

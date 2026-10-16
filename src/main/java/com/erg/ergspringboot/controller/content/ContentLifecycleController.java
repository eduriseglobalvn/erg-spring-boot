package com.erg.ergspringboot.controller.content;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.content.lifecycle.ContentLifecycleOperations;
import com.erg.ergspringboot.content.lifecycle.ContentLifecycleRequests.TransitionRequest;
import com.erg.ergspringboot.content.lifecycle.ContentLifecycleResponses.LifecycleResponse;
import com.erg.ergspringboot.content.lifecycle.LifecycleResourceKind;
import com.erg.ergspringboot.content.lifecycle.authorization.ContentLifecycleTransition;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Typed lifecycle command boundary for admin content authoring. */
@RestController
public class ContentLifecycleController {
    private final ContentLifecycleOperations lifecycle;

    public ContentLifecycleController(ContentLifecycleOperations lifecycle) {
        this.lifecycle = lifecycle;
    }

    @PostMapping("/api/v1/content/{resourceKind}/{resourceId}/lifecycle")
    @ContentLifecycleTransition
    public ApiResponse<LifecycleResponse> transition(
            @PathVariable String resourceKind,
            @PathVariable String resourceId,
            @Valid @RequestBody TransitionRequest body,
            HttpServletRequest request) {
        LifecycleResponse response = lifecycle.transition(
                UserAccessContext.from(request), LifecycleResourceKind.parse(resourceKind), resourceId, body);
        return ApiResponse.success(200, "Content lifecycle transitioned", response,
                request.getRequestURI(), com.erg.ergspringboot.platform.web.RequestCorrelation.current(request));
    }
}

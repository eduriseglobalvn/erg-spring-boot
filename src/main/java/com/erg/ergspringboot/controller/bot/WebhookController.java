package com.erg.ergspringboot.controller.bot;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.service.support.ModuleWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@org.springframework.context.annotation.Profile("prototype")
public class WebhookController {

    private final ModuleWorkflowService workflows;

    public WebhookController(ModuleWorkflowService workflows) {
        this.workflows = workflows;
    }

    @PostMapping({"/webhooks/discord", "/webhooks/telegram"})
    public ApiResponse<Map<String, Object>> webhook(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ApiResponse.success(200, "Webhook accepted", workflows.accepted("bot", request.getRequestURI(), body), request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}

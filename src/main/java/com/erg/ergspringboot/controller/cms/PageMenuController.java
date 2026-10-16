package com.erg.ergspringboot.controller.cms;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.service.cms.CmsService;
import com.erg.ergspringboot.platform.security.ReviewedPublicEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@org.springframework.context.annotation.Profile("prototype")
@RequestMapping("/api/v1")
public class PageMenuController {

    private final CmsService cmsService;

    public PageMenuController(CmsService cmsService) {
        this.cmsService = cmsService;
    }

    @GetMapping("/pages/{slug}")
    @ReviewedPublicEndpoint
    public ApiResponse<Map<String, Object>> page(@PathVariable String slug, HttpServletRequest request) {
        return ok("Page loaded", cmsService.bySlug("pages", slug), request);
    }

    @GetMapping("/menus/structure")
    @ReviewedPublicEndpoint
    public ApiResponse<Map<String, Object>> menu(HttpServletRequest request) {
        return ok("Menu loaded", cmsService.menuStructure(), request);
    }

    private ApiResponse<Map<String, Object>> ok(String message, Map<String, Object> data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return com.erg.ergspringboot.platform.web.RequestCorrelation.current(request);
    }
}

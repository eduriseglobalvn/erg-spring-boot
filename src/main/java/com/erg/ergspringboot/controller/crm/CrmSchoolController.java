package com.erg.ergspringboot.controller.crm;

import com.erg.ergspringboot.common.dto.crm.CrmSchoolDtos.CrmSchoolListResponse;
import com.erg.ergspringboot.common.dto.crm.CrmSchoolDtos.CrmSchoolPerformanceResponse;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.service.crm.CrmSchoolService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/crm")
public class CrmSchoolController {

    private final CrmSchoolService crmSchoolService;

    public CrmSchoolController(CrmSchoolService crmSchoolService) {
        this.crmSchoolService = crmSchoolService;
    }

    @GetMapping("/seo/{seoId}/schools")
    public ApiResponse<CrmSchoolListResponse> schoolsBySeo(
            @PathVariable String seoId,
            @RequestParam(defaultValue = "false") boolean signed,
            HttpServletRequest request
    ) {
        CrmSchoolListResponse data = new CrmSchoolListResponse(
                signed ? crmSchoolService.signedSchoolsBySeo(seoId) : crmSchoolService.signedSchoolsBySeo(seoId)
        );
        return ok("CRM schools loaded", data, request);
    }

    @GetMapping("/seo/{seoId}/performance")
    public ApiResponse<CrmSchoolPerformanceResponse> performance(
            @PathVariable String seoId,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            HttpServletRequest request
    ) {
        return ok("CRM SEO performance loaded", crmSchoolService.performance(seoId, from, to), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}

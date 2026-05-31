package com.erg.ergspringboot.controller.organization;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.organization.EducationUnitResponses.EducationUnitListResponse;
import com.erg.ergspringboot.service.organization.EducationUnitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/education-units", "/api/v1/centers"})
public class EducationUnitController {

    private final EducationUnitService educationUnitService;

    public EducationUnitController(EducationUnitService educationUnitService) {
        this.educationUnitService = educationUnitService;
    }

    @GetMapping
    public ApiResponse<EducationUnitListResponse> listEducationUnits(HttpServletRequest request) {
        return ApiResponse.success(200, "Education units loaded", educationUnitService.listEducationUnits(), request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}

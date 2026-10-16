package com.erg.ergspringboot.controller.organization;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.organization.EducationUnitResponses.EducationUnitListResponse;
import com.erg.ergspringboot.organization.OrganizationOperations;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/education-units", "/api/v1/centers"})
public class EducationUnitController {

    private final OrganizationOperations organization;

    public EducationUnitController(OrganizationOperations organization) {
        this.organization = organization;
    }

    @GetMapping
    public ApiResponse<EducationUnitListResponse> listEducationUnits(HttpServletRequest request) {
        return ApiResponse.success(200, "Education units loaded", organization.listEducationUnits(UserAccessContext.from(request)), request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return com.erg.ergspringboot.platform.web.RequestCorrelation.current(request);
    }
}

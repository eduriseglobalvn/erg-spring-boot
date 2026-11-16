package com.erg.ergspringboot.authorization.catalog;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.platform.web.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only view of the role catalog for the "Vai trò & quyền" page. */
@RestController
@RequestMapping("/api/v1/rbac")
public class RbacCatalogController {

    @GetMapping("/catalog")
    @PreAuthorize("@methodAuthorization.isAllowedAny(authentication, 'rbac.role.read', '*')")
    public ApiResponse<Map<String, Object>> catalog(HttpServletRequest request) {
        List<Map<String, Object>> functions = new ArrayList<>();
        for (PermissionFunction function : PermissionFunction.values()) {
            functions.add(Map.of("code", function.name(), "label", function.label()));
        }
        List<Map<String, Object>> roles = new ArrayList<>();
        Map<String, Map<String, String>> matrix = new LinkedHashMap<>();
        for (BusinessRole role : BusinessRoleCatalog.all()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", role.id());
            view.put("code", role.code());
            view.put("label", role.label());
            view.put("description", role.description());
            view.put("canDo", role.canDo());
            view.put("cannotDo", role.cannotDo());
            view.put("exclusive", role.exclusive());
            roles.add(view);
            Map<String, String> cells = new LinkedHashMap<>();
            for (PermissionFunction function : PermissionFunction.values()) {
                cells.put(function.name(), BusinessRoleCatalog.cell(role, function));
            }
            matrix.put(role.id(), cells);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("functions", functions);
        body.put("roles", roles);
        body.put("matrix", matrix);
        return ApiResponse.success(200, "Success", body, request.getRequestURI(), RequestCorrelation.current(request));
    }
}

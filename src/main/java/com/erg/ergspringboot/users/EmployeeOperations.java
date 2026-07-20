package com.erg.ergspringboot.users;

import com.erg.ergspringboot.common.dto.users.EmployeeDtos.*;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.users.EmployeeManagementService;
import com.erg.ergspringboot.service.users.EmployeeSecurityActivityService;
import org.springframework.stereotype.Service;

/** Public employee-management boundary for the users context. */
@Service
public class EmployeeOperations {
    private final EmployeeManagementService management;
    private final EmployeeSecurityActivityService security;

    public EmployeeOperations(EmployeeManagementService management, EmployeeSecurityActivityService security) {
        this.management = management;
        this.security = security;
    }

    public EmployeeListResponse listEmployees(String tenantId, Integer page, Integer limit, String search,
            String status, String role) { return management.listEmployees(tenantId, page, limit, search, status, role); }
    public SecurityFeedPageResponse listUnifiedFeed(String tenantId, String userId, String sourceType,
            Integer page, Integer limit) { return security.listUnifiedFeed(tenantId, userId, sourceType, page, limit); }
    public SecurityEventPageResponse listSecurityEvents(String userId, String tenantId, Integer page, Integer limit) {
        return security.listSecurityEvents(userId, tenantId, page, limit);
    }
    public EmployeeCreatedResponse createEmployee(String tenantId, CreateEmployeeRequest request,
            UserAuditContext audit) { return management.createEmployee(tenantId, request, audit.toLegacy()); }
    public EmployeeDetailResponse getEmployee(String userId, String tenantId) { return management.getEmployee(userId, tenantId); }
    public EmployeeDetailResponse updateEmployee(String userId, String tenantId, UpdateEmployeeRequest request,
            UserAuditContext audit) { return management.updateEmployee(userId, tenantId, request, audit.toLegacy()); }
}

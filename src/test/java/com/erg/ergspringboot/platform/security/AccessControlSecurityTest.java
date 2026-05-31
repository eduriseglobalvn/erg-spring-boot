package com.erg.ergspringboot.platform.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.ErgSpringApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(classes = ErgSpringApplication.class)
@AutoConfigureMockMvc
class AccessControlSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void accessControlRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void accessControlReturnsForbiddenWhenPermissionIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                        .header("X-User-ID", "user-1")
                        .header("X-Permissions", "lms.quiz.read")
                        .header("X-Portals", "*"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));
    }

    @Test
    void accessControlAllowsRoleWithRequiredPermission() throws Exception {
        mockMvc.perform(get("/api/v1/platform/tenancy")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "platform.read")
                        .header("X-Portals", "*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tenancy config loaded"));
    }

    @Test
    void accessControlDeniesWhenDenyOverrideMatches() throws Exception {
        mockMvc.perform(post("/api/v1/roles")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .header("X-Denied-Permissions", "rbac.role.create")
                        .header("X-Portals", "*")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "blocked-role",
                                  "permissions": []
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("permission")));
    }

    @Test
    void lmsEducationUnitListUsesSpecificUnitReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/lms/centers")
                        .header("X-User-ID", "teacher")
                        .header("X-Permissions", "lms.unit.read")
                        .header("X-Managed-EducationUnit-IDs", "sch_binh_an"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deprecated").value(true))
                .andExpect(jsonPath("$.data.replacement").value("/api/v1/sessions/current"))
                .andExpect(jsonPath("$.data.scope.managedEducationUnitIds[0]").value("sch_binh_an"));
    }

    @Test
    void lmsGenericReadDoesNotGrantScopedManagementSurfaces() throws Exception {
        mockMvc.perform(get("/api/v1/lms/access-management/users")
                        .header("X-User-ID", "reader")
                        .header("X-Permissions", "lms.read")
                        .header("X-Managed-EducationUnit-IDs", "sch_binh_an"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors").value("ACCESS_DENIED"));
    }

    @Test
    void cmsPostCreateUsesSpecificCmsPermission() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .contentType("application/json")
                        .header("X-User-ID", "editor")
                        .header("X-Permissions", "cms.posts.create")
                        .content("""
                                {
                                  "title": "Permissioned post",
                                  "status": "draft"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("draft"));
    }

    @Test
    void crmSchoolListUsesSchoolReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/crm/seo/seo_1/schools")
                        .header("X-User-ID", "seo_1")
                        .header("X-Permissions", "crm.school.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("CRM schools loaded"));
    }

    @Test
    void crmPerformanceRequiresAnalyticsPermission() throws Exception {
        mockMvc.perform(get("/api/v1/crm/seo/seo_1/performance")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-05-26T00:00:00Z")
                        .header("X-User-ID", "seo_1")
                        .header("X-Permissions", "crm.school.read"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors").value("ACCESS_DENIED"));
    }

    @Test
    void crmPerformanceAllowsAnalyticsPermission() throws Exception {
        mockMvc.perform(get("/api/v1/crm/seo/seo_1/performance")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-05-26T00:00:00Z")
                        .header("X-User-ID", "seo_1")
                        .header("X-Permissions", "crm.analytics.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("CRM SEO performance loaded"));
    }

}

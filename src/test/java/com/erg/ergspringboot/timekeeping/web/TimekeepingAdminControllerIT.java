package com.erg.ergspringboot.timekeeping.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.timekeeping.internal.admin.AdminITBase;
import com.erg.ergspringboot.timekeeping.internal.request.RequestServiceIT.Fixture;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/** Auth and scoping basics of /api/v1/timekeeping/admin and /approvals. */
class TimekeepingAdminControllerIT extends AdminITBase {

    private static final String T = "tk-admin-web";
    private static final String ADMIN = "/api/v1/timekeeping/admin";
    private static final String APPROVALS = "/api/v1/timekeeping/approvals";

    @Autowired MockMvc mvc;

    private Fixture f;

    @BeforeEach
    void seed() {
        f = Fixture.seed(jdbc, T, "aw");
    }

    @Test
    void aTeacherCannotUseTheAdminOrApprovalApi() throws Exception {
        mvc.perform(as(get(ADMIN + "/today"), f.teacher(), "timekeeping.self")).andExpect(status().isForbidden());
        mvc.perform(as(get(APPROVALS), f.teacher(), "timekeeping.self")).andExpect(status().isForbidden());
        // an approver without manage cannot open the ALL tab or the admin API
        mvc.perform(as(get(APPROVALS).param("box", "ALL"), f.lead(), "timekeeping.approve"))
                .andExpect(status().isForbidden());
        mvc.perform(as(get(ADMIN + "/sites"), f.lead(), "timekeeping.approve")).andExpect(status().isForbidden());
    }

    @Test
    void anAdminSeesTodayButNotAnotherTenantsSchool() throws Exception {
        mvc.perform(as(get(ADMIN + "/today").param("schoolId", f.school()), "admin", "timekeeping.manage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-11-12"));
        jdbc.update("insert into education_units (id, tenant_id, name, slug, type, status, created_at, updated_at)"
                + " values ('sch_other_tenant', 'tk-other', 'Khác', 'sch_other_tenant', 'school', 'active', now(), now())");
        mvc.perform(as(get(ADMIN + "/today").param("schoolId", "sch_other_tenant"), "admin", "timekeeping.manage"))
                .andExpect(status().isNotFound());
        mvc.perform(as(get(ADMIN + "/timesheet").param("schoolId", f.school()).param("period", "11-2026"), "admin",
                "timekeeping.manage")).andExpect(status().isBadRequest());
    }

    @Test
    void siteCrudValidatesAndDeletesWith204() throws Exception {
        String site = """
                {"schoolId":"%s","name":"Cổng chính","lat":21.0285,"lng":105.8542,"radiusM":%d}""";
        mvc.perform(as(post(ADMIN + "/sites").contentType(MediaType.APPLICATION_JSON)
                .content(site.formatted(f.school(), 40)), "admin", "timekeeping.manage"))
                .andExpect(status().isBadRequest());
        String body = mvc.perform(as(post(ADMIN + "/sites").contentType(MediaType.APPLICATION_JSON)
                .content(site.formatted(f.school(), 150)), "admin", "timekeeping.manage"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.data.id");

        mvc.perform(as(delete(ADMIN + "/sites/" + id), "admin", "timekeeping.manage")).andExpect(status().isNoContent());
        mvc.perform(as(delete(ADMIN + "/sites/" + id), "admin", "timekeeping.manage")).andExpect(status().isNotFound());
    }

    @Test
    void anApproverWhoIsNotOnTheRequestGets404AndRejectNeedsAComment() throws Exception {
        String id = request(T, f.school(), f.teacher(), "LEAVE", "SICK", LocalDate.of(2026, 11, 16),
                LocalDate.of(2026, 11, 16), "PENDING");
        jdbc.update("insert into tk_request_steps (id, tenant_id, request_id, step_no, resolver, approver_id)"
                + " values ('st-aw-1', ?, ?, 1, 'MANAGER', ?)", T, id, f.lead());

        mvc.perform(as(get(APPROVALS + "/" + id), f.vp(), "timekeeping.approve")).andExpect(status().isNotFound());
        mvc.perform(as(post(APPROVALS + "/" + id + "/approve"), f.vp(), "timekeeping.approve"))
                .andExpect(status().isNotFound());
        mvc.perform(as(get(APPROVALS + "/" + id), f.lead(), "timekeeping.approve")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canAct").value(true))
                .andExpect(jsonPath("$.data.actingOnBehalf").value(false));
        mvc.perform(as(post(APPROVALS + "/" + id + "/reject").contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\" \"}"), f.lead(), "timekeeping.approve")).andExpect(status().isBadRequest());
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String user, String perms) {
        return request.header("X-Tenant-ID", T).header("X-User-ID", user)
                .header("X-Roles", "lms.instructor").header("X-Permissions", perms);
    }
}

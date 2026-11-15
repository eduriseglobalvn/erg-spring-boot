package com.erg.ergspringboot.timekeeping.internal.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.TeachingScheduleFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** B4: accounts provisioned with a school-scoped role assignment (no user_access_scopes row) still belong to the school. */
class WorkContextSchoolOfIT extends TeachingScheduleFixtures {

    @Autowired WorkContextResolver context;

    @Test
    void aSchoolScopedRoleAssignmentPlacesTheTeacherInTheSchoolAndARevokedOneDoesNot() {
        String tenant = id();
        String school = school(tenant, "THCS Nguyen Du");
        write("insert into tk_shifts (id, tenant_id, school_id, name, start_time, end_time, work_units, weekdays, created_at)"
                + " values (?, ?, ?, 'Ca sang', '07:30', '11:30', 1.0, '{1,2,3,4,5}', '2020-01-01')", id(), tenant, school);
        String admin = user(tenant, "Admin");
        String teacher = user(tenant, "Co giao");
        String revoked = user(tenant, "Da thu hoi");
        String role = jdbc.queryForObject("select id from roles where name = 'lms.instructor'", String.class);
        assign(tenant, teacher, role, school, admin, false);
        assign(tenant, revoked, role, school, admin, true);

        assertThat(context.schoolOf(tenant, teacher)).contains(school);
        assertThat(context.schoolOf(tenant, revoked)).isEmpty();
        assertThat(context.schoolsOf(tenant, java.util.List.of(teacher, revoked))).containsOnlyKeys(teacher);
    }

    private void assign(String tenant, String user, String role, String school, String by, boolean revoked) {
        write("insert into user_role_assignments (id, user_id, role_id, tenant_id, scope_type, scope_id, assigned_by, revoked_at)"
                + " values (?, ?, ?, ?, 'SCHOOL', ?, ?, " + (revoked ? "now()" : "null") + ")", id(), user, role, tenant, school, by);
    }
}

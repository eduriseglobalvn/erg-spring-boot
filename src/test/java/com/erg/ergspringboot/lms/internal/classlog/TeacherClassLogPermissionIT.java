package com.erg.ergspringboot.lms.internal.classlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.TeachingScheduleFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/** B3: V54 stripped class-log and attendance write from teachers; V159 gives them back. */
class TeacherClassLogPermissionIT extends TeachingScheduleFixtures {

    private List<String> grants(String role) {
        return jdbc.queryForList("select p.name from roles r join role_permissions rp on rp.role_id = r.id"
                + " join permissions p on p.id = rp.permission_id where r.name = ?", String.class, role);
    }

    @Test
    void everyTeacherRoleThatMayWriteTheClassLogAndAttendanceHoldsThePermissions() {
        assertThat(grants("lms.instructor")).contains("lms.class_log.read", "lms.class_log.write",
                "lms.attendance.read", "lms.attendance.write");
        for (String role : List.of("lms_teacher_standard", "lms_homeroom_teacher", "lms_subject_teacher",
                "lms_teacher_assistant", "lms_teacher_lead")) {
            assertThat(grants(role)).as(role).contains("lms.class_log.read", "lms.class_log.write", "lms.attendance.write");
        }
        assertThat(grants("lms_teacher_readonly")).contains("lms.class_log.read")
                .doesNotContain("lms.class_log.write", "lms.attendance.write");
    }
}

package com.erg.ergspringboot.lms.internal.classlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.TeachingScheduleFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/** B3: teachers write the class log and attendance (V159); since V160 the one teacher role is Giáo viên, Vận hành only reads. */
class TeacherClassLogPermissionIT extends TeachingScheduleFixtures {

    private List<String> grants(String role) {
        return jdbc.queryForList("select p.name from roles r join role_permissions rp on rp.role_id = r.id"
                + " join permissions p on p.id = rp.permission_id where r.name = ?", String.class, role);
    }

    @Test
    void theTeacherRoleWritesTheClassLogAndAttendanceAndOperationsOnlyReads() {
        assertThat(grants("lms.instructor")).contains("lms.class_log.read", "lms.class_log.write",
                "lms.attendance.read", "lms.attendance.write");
        assertThat(grants("operations")).contains("lms.class_log.read")
                .doesNotContain("lms.class_log.write", "lms.attendance.write");
    }
}
